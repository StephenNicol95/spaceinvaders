package spaceinvaders;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Point;
import java.awt.Transparency;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;

import leaderboard.LeaderboardClient;
import runners.Main;

@SuppressWarnings("serial")
public class GameCanvas extends JPanel implements ActionListener {
	private static transient GameCanvas gameCanvas = null;
	private static final int NUMBER_ENEMIES = 28;
	private Player player;
	private ArrayList<Enemy> enemies = new ArrayList<Enemy>();
	private ArrayList<Laser> enemyLaserCleanUpList = new ArrayList<Laser>();
	private ArrayList<Laser> playerLaserCleanUpList = new ArrayList<Laser>();
	private static HashSet<GameObject> gameObjects = new HashSet<GameObject>();
	private int score = 0;
	private static HashMap<String, Image> imageCache = new HashMap<String, Image>();
	private long lastFire;
	private int enemyCount = NUMBER_ENEMIES;
	private int liveCount = 3;
	private int levelCount = 1;
	private Timer leftPressed = new Timer(10, new LeftPressed());
	private Timer rightPressed = new Timer(10, new RightPressed());
	private Timer gameTimer = new Timer(20, this);
	private int speed = 1;
	private Timer enemyFireTimer = new Timer(1000/speed, new MyFireListener());
	private Timer respawn = new Timer(1500, new redrawPlayer());
	
	static Vector<String> listVector = new Vector<String>();
	
	private GameCanvas() {
		super();
		setBackground(Color.WHITE);
		setDoubleBuffered(true);
		setFocusable(true);
		initShips();
		createPlayer();
		
		addKeyListener(keyListener);
		gameTimer.start();
		
		enemyFireTimer.start();
		this.setBackground(Color.BLACK);
	}

	public static GameCanvas getGameCanvas(boolean whichCanvas) {
		gameCanvas = (gameCanvas == null? new GameCanvas() : gameCanvas);
		return gameCanvas;
	}
	
	private void initShips() {
		for (int i = 0; i < NUMBER_ENEMIES/4; i++) { //Forced 7 for 28
			for (int j = 0; j < NUMBER_ENEMIES/7; j++) { //Forced 4 for 28
				Enemy e = new Enemy(Main.RUNNINGLOCATION + (Main.USINGECLIPSE ? "/src/spaceinvaders/resources/Enemy.jpg" : "\\spaceinvaders\\resources\\Enemy.jpg"), new Point(100 + i*50, (50)+j*30), new Dimension(50, 30));
				enemies.add(e);
				gameObjects.add(e);
			}
		}
	}
	
	private void createPlayer() {
		player = new Player(Main.RUNNINGLOCATION + (Main.USINGECLIPSE ? "/src/spaceinvaders/resources/Ship.png" : "\\spaceinvaders\\resources\\Ship.png"), new Point(300, 500), new Dimension(60, 30));
		gameObjects.add(player);
	}
	
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		g.setColor(Color.WHITE);
		g.drawString("Lives:  " + liveCount, 500, 30);
		g.drawString("Level: " + levelCount, 270, 30);
		g.drawString("Score:  " + score, 50, 30);
		
		Iterator<GameObject> itt = gameObjects.iterator();
		
		while (itt.hasNext()) {
			GameObject current = itt.next();
			if (!current.isActive())
				continue;
			current.draw(g);
		}
	}
	
	@Override
	public void actionPerformed(ActionEvent event) {
		int moveY = 0;
		
		for (int i = 0; i < enemies.size(); i++) {
			if (enemies.get(i).switchDirection()) {
				moveY = 10;
				break;
			} else {
				moveY = 0;
			}
		}
		
		Iterator<GameObject> itt = gameObjects.iterator();
		
		while (itt.hasNext()) {
			GameObject current = itt.next();
			
			if (current instanceof Player) 
				continue; // we don't want the player to be automatically updated
			
			current.move(0, moveY);
		}
		collision();
		cleanUp();
		repaint();
	}
	
	public Enemy[] getEnemies() {
		return (Enemy[]) enemies.toArray();
	}
	
	public static void addGameObject(GameObject obj) {
		gameObjects.add(obj);
	}
	
	public static void removeGameObject(GameObject obj) {
		if (gameObjects.contains(obj))
			gameObjects.remove(obj);
	}
	
	
	private void collision() {	
		// test players for hitting enemy
		if(enemies.size() > 0) {
			for (Enemy e : enemies) {
				Iterator<Laser> laserItt = Player.laserList.iterator();
				while (laserItt.hasNext()) {
					Laser currentLaser = (Laser) laserItt.next();
					
					if (currentLaser.getRectangle().intersects(e.getRectangle()) && currentLaser.isActive()) {
						currentLaser.setActive(false);
						playerLaserCleanUpList.add(currentLaser);
						score += 10;
						e.setActive(false);
						enemyCount--;
					}
				}
			}
			
			//Test for resetting the board
			if (enemyCount == 0) {
				try {
					Thread.sleep(500);
				} catch(Exception e) {
				}
				if(liveCount < 3)
					liveCount++;
				levelCount++;
				enemyCount = NUMBER_ENEMIES;
				speed++;
				enemyFireTimer = new Timer(1000/speed, new MyFireListener());
				cleanUp();
				initShips();
			}
		}
		
		// test enemy laser hitting player
		Iterator<Laser> laserItt = Enemy.laserList.iterator();
		while (laserItt.hasNext()) {
			Laser currentLaser = (Laser) laserItt.next();
			
			if(player.isActive()) {
				if (currentLaser.getRectangle().intersects(player.getRectangle()) && currentLaser.isActive()) {
					currentLaser.setActive(false);
					enemyLaserCleanUpList.add(currentLaser);
					enemyFireTimer.stop();
					gameTimer.stop();
					respawn.start();
					player.setActive(false);
					
					if (liveCount >=  1) {
						liveCount--;
					}
				}
			}
		}
		
		//If the player is dead after the laser finishes, request the leaderboard data
		if(!player.isActive() && liveCount < 1) {
			String username = JOptionPane.showInputDialog("What would you like your username to be (note: username will be submitted as the first 3 letters and all uppercase)?");
			if(username.length() > 3) {
				username = username.substring(0, 3);
			} else if(username.equals("")) {
				username = "NAN";
			}
			username.toUpperCase();
			LeaderboardClient.getInstance().spaceInvadersRequestLeaderboards(username + " " + score);
		}
	}

	private void cleanUp() {
		ArrayList<Enemy> al = new ArrayList<Enemy>();
		for (Enemy e : enemies) {
			if (!e.isActive()) {
				al.add(e);
			}
		}
		enemies.removeAll(al);
		Player.laserList.removeAll(playerLaserCleanUpList);
		Enemy.laserList.removeAll(enemyLaserCleanUpList);
	}
	
	public static Image getImage(String location, GraphicsConfiguration gc) throws IOException {
		Image img = null;
		if (imageCache.containsKey(location)) {
			return imageCache.get(location);
		} else {
			Image sourceImg;
			sourceImg = ImageIO.read(new File(location));
			img = gc.createCompatibleImage(sourceImg.getWidth(null), sourceImg.getHeight(null), Transparency.BITMASK);
			img.getGraphics().drawImage(sourceImg, 0, 0, null);
			imageCache.put(location, img);
		}
		return img;
	}
	
	public class MyFireListener implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent arg0) {
			if (enemies.size() == 0) {
				return;
			} else {
				int randomNumber = new Random().nextInt(enemies.size());
				enemies.get(randomNumber).fire();
			}
		}
	}
	
	KeyListener keyListener = new KeyListener() {
		@Override
		public void keyPressed(KeyEvent e) {
			
			if (e.getKeyCode() == KeyEvent.VK_LEFT) {
				rightPressed.start();
				
				if (player.getPosition().x < 0) {
					rightPressed.stop();
				}
			}
			
			if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
				leftPressed.start();
				
				if (player.getPosition().x > 550) {
					leftPressed.stop();
				}
			}
		}

		@Override
		public void keyReleased(KeyEvent e) {
			if (e.getKeyCode() == KeyEvent.VK_LEFT) {
				rightPressed.stop();
			}
			
			if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
				leftPressed.stop();
			}
			
			if (e.getKeyCode() == KeyEvent.VK_SPACE) {
				int delay = 200;
				
				if (System.currentTimeMillis() - lastFire < delay) {
					return;
				}
				if (player.isActive()) {
					player.fire();
					lastFire = System.currentTimeMillis();
				}
			}
		}

		@Override
		public void keyTyped(KeyEvent e) {}
	};
	
	class RightPressed implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent e) {
			if (player.isActive())
				player.move(player.getPosition().x-5, 0);
		}
		
	}
	
	class LeftPressed implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent e) {
			if (player.isActive())
				player.move(player.getPosition().x+5, 0);
		}
	}
	
	class redrawPlayer implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent arg0) {
			respawn.stop();
			
			if (liveCount != 0) {
				createPlayer();
				player.setActive(true);
				enemyFireTimer.start();
				gameTimer.start();
			}
		}
	}
}
