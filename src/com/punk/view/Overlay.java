package com.punk.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SpringLayout;

import com.punk.model.Capturepoint;
import com.punk.model.CapturepointsUtil;
import com.punk.model.GuiOptions;
import com.punk.mumblelink.MumbleLink;
import com.punk.resources.Resources;
import com.punk.start.Start;
import com.punk.start.Start.Border;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.win32.W32APIOptions;

/**
 * @author Sander Schulenburg aka "Much"(schulenburgsander@gmail.com)
 */
public class Overlay extends Thread {

	public enum Type {
		Text, Icons
	}

	public enum Size {
		SMALL, MEDIUM, LARGE
	}

	private SpringLayout overlayPanelSpringLayout = null;
	private JFrame overlayFrame = null;
	private RichJPanel overlayPanel = null;
	private boolean isMouseOnOverlay = false;

	private CapturepointsUtil capUtil = null;
	private Border border = null;
	private Size size = null;
	private boolean showNames = false;
	private boolean showBackground = true;
	private boolean copyToClipboard = false;

	private MumbleLink mumbleLink;

	private JLabel labelPlayer = new JLabel();
	private HashMap<String, JLabel> players = new HashMap<String, JLabel>();

	private GuiOptions guiOptions = GuiOptions.getInstance();

	private Timer timer = null;

	private JProgressBar nextUpdateBar = new JProgressBar(0, 100);

	private User32 user32;

	private int waypointTime = 0;
	private JPanel waypointTimerPanel = null;

	public Overlay(CapturepointsUtil capUtil, Border border, Overlay.Type type,
			Overlay.Size size) {
		this.capUtil = capUtil;
		this.border = border;
		this.size = size;

		mumbleLink = new MumbleLink();

		overlayFrame = new JFrame();
		overlayFrame.setUndecorated(true);
		overlayFrame.setSize(0, 0);
		overlayFrame.setLocationRelativeTo(null);
		overlayFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		overlayFrame.setAlwaysOnTop(true);
		overlayFrame.setBackground(new Color(1.0f, 1.0f, 1.0f, 0.0f));
		overlayFrame.setLayout(new BorderLayout());
		overlayFrame.setVisible(false);

		overlayFrame.setFocusableWindowState(false);
		overlayFrame.setFocusable(false);
		overlayFrame.enableInputMethods(false);

		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		overlayFrame.setLocation((int) screenSize.getWidth() - getWidth(),
				(int) screenSize.getHeight() - getHeight());
		overlayFrame.setSize(getWidth(), getHeight());
		guiOptions.setxLocation(overlayFrame.getX());
		guiOptions.setyLocation(overlayFrame.getY());

		overlayPanelSpringLayout = new SpringLayout();
		overlayPanel = new RichJPanel(overlayPanelSpringLayout);
		overlayPanel.setBackground(new Color(1.0f, 1.0f, 1.0f, 0.0f));
		overlayFrame.add(overlayPanel, BorderLayout.CENTER);

		nextUpdateBar.setBackground(new Color(1.0f, 1.0f, 1.0f, 0.0f));
		nextUpdateBar.setForeground(Capturepoint.GRAY);
		nextUpdateBar.setBorderPainted(false);
		overlayFrame.add(nextUpdateBar, BorderLayout.SOUTH);

		updateOverlayFrame();

		timer = new Timer();
		timer.schedule(new updateTimers(), 0, 1000);

		user32 = (User32) Native.loadLibrary("user32", User32.class,
				W32APIOptions.DEFAULT_OPTIONS);
	}

	public void setLocation(int x, int y) {
		overlayFrame.setLocation(x - overlayFrame.getWidth(),
				y - overlayFrame.getHeight());
	}

	private void applyPosition() {
		overlayFrame.setLocation(guiOptions.getxLocation(),
				guiOptions.getyLocation());
		overlayFrame.setSize(getWidth(), getHeight());
	}

	public int getWidth() {
		return (int) (500 * getSizeMultiplier());
	}

	public int getHeight() {
		switch (border) {
		case EB:
			return (int) (500 * getSizeMultiplier());

		default:
			return (int) (680 * getSizeMultiplier());
		}
	}

	private double getSizeMultiplier() {
		switch (size) {
		case SMALL:
			return 0.60;
		case MEDIUM:
			return 0.75;
		default:
			return 1;
		}

	}

	public void setWaypointTime(int time) {
		waypointTime = time;
		if (waypointTime > 0) {
			((JLabel) waypointTimerPanel.getComponent(0))
					.setIcon(getWaypointIcon());
		}
	}

	public void setBorder(Border border) {
		clearOverlayFrame();

		this.border = border;
		switch (border) {
		case GREEN:
			nextUpdateBar.setForeground(Capturepoint.GREEN);
			break;
		case BLUE:
			nextUpdateBar.setForeground(Capturepoint.BLUE);
			break;
		case RED:
			nextUpdateBar.setForeground(Capturepoint.RED);
			break;
		default:
			nextUpdateBar.setForeground(Capturepoint.GRAY);
			break;
		}

		updateOverlayFrame();
	}

	public Border getBorder() {
		return border;
	}

	public int getBorderId() {
		switch (border) {
		case EB:
			return 38;
		case RED:
			return 94;
		case BLUE:
			return 96;
		case GREEN:
			return 95;
		}
		return -1;
	}

	public int getBorderObjectCount() {
		switch (border) {
		case EB:
			return 22;
		case RED:
			return 13;
		case BLUE:
			return 13;
		case GREEN:
			return 13;
		}
		return 0;
	}

	public Size getSize() {
		return size;
	}

	public void clearOverlayFrame() {
		ArrayList<Capturepoint> capturepoints = capUtil
				.getCapturepoints(this.border);
		for (Capturepoint capturepoint : capturepoints) {
			overlayPanel.remove(capturepoint.getOverlay());
		}
		overlayPanel.remove(waypointTimerPanel);
	}

	public void updateOverlayFrame() {
		double sizeMultiplier = getSizeMultiplier();

		if (showBackground) {
			switch (border) {
			case EB:
				overlayPanel.setBackgroundImage(Resources.IMAGE_MAP_EB,
						sizeMultiplier, guiOptions.getBackgroundAlpha());
				break;
			default:
				overlayPanel.setBackgroundImage(Resources.IMAGE_MAP_BORDER,
						sizeMultiplier, guiOptions.getBackgroundAlpha());
				break;
			}
		} else {
			overlayPanel.setBackgroundImage(null, 0, 0);
		}

		ArrayList<Capturepoint> capturepoints = capUtil
				.getCapturepoints(border);

		for (Capturepoint capturepoint : capturepoints) {
			capturepoint.createOverlay(showNames, getSizeMultiplier());
			JPanel overlay = capturepoint.getOverlay();
			overlayPanel.add(overlay);

			overlayPanelSpringLayout.putConstraint(
					SpringLayout.HORIZONTAL_CENTER, overlay,
					(int) ((double) capturepoint.getTop() * sizeMultiplier),
					SpringLayout.WEST, overlayPanel);
			overlayPanelSpringLayout.putConstraint(SpringLayout.NORTH, overlay,
					(int) ((double) capturepoint.getLeft() * sizeMultiplier),
					SpringLayout.NORTH, overlayPanel);
		}

		waypointTimerPanel = new JPanel();
		waypointTimerPanel.setBackground(new Color(1.0f, 1.0f, 1.0f, 0.0f));
		waypointTimerPanel.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.CENTER;
		c.gridx = 0;
		c.gridy = 0;

		RichJLabel labelOverlayName = new RichJLabel(
				getTimeAsString(waypointTime), 0);
		labelOverlayName.setForeground(Color.CYAN);
		labelOverlayName.setRightShadow(1, 1, Color.BLACK);
		waypointTimerPanel.add(labelOverlayName, c);
		labelOverlayName.setVisible(true);

		JLabel labelOverlayIcon = new JLabel(getWaypointIcon());
		waypointTimerPanel.add(labelOverlayIcon, c);
		waypointTimerPanel.add(labelOverlayName);
		overlayPanel.add(waypointTimerPanel);

		overlayPanelSpringLayout.putConstraint(SpringLayout.HORIZONTAL_CENTER,
				waypointTimerPanel, (int) ((double) 50 * sizeMultiplier),
				SpringLayout.WEST, overlayPanel);
		overlayPanelSpringLayout.putConstraint(SpringLayout.NORTH,
				waypointTimerPanel, (int) ((double) 5 * sizeMultiplier),
				SpringLayout.NORTH, overlayPanel);

		applyPosition();
		overlayPanel.repaint();
	}

	private ImageIcon getWaypointIcon() {
		if (waypointTime > 0) {
			ImageIcon icon = Resources.IMAGE_WAYPOINT_CONTESTED;
			return new ImageIcon(
					icon.getImage().getScaledInstance(
							(int) (Resources.IMAGE_WAYPOINT_CONTESTED
									.getIconWidth() * getSizeMultiplier()),
							(int) (Resources.IMAGE_WAYPOINT_CONTESTED
									.getIconHeight() * getSizeMultiplier()),
							Image.SCALE_SMOOTH));
		}
		ImageIcon icon = Resources.IMAGE_WAYPOINT;
		return new ImageIcon(
				icon.getImage()
						.getScaledInstance(
								(int) (Resources.IMAGE_WAYPOINT.getIconWidth() * getSizeMultiplier()),
								(int) (Resources.IMAGE_WAYPOINT.getIconHeight() * getSizeMultiplier()),
								Image.SCALE_SMOOTH));
	}

	public void setSize(Size size) {
		clearOverlayFrame();

		this.size = size;

		updateOverlayFrame();
	}

	public ImageIcon getProfIcon(int prof) {
		switch (prof) {
		case 0:
			return Resources.IMAGE_COMMANDER;
		case 1:
			return Resources.IMAGE_CLASS_GUARDIAN;
		case 2:
			return Resources.IMAGE_CLASS_WARRIOR;
		case 3:
			return Resources.IMAGE_CLASS_ENGINEER;
		case 4:
			return Resources.IMAGE_CLASS_RANGER;
		case 5:
			return Resources.IMAGE_CLASS_THIEF;
		case 6:
			return Resources.IMAGE_CLASS_ELEMENTALIST;
		case 7:
			return Resources.IMAGE_CLASS_MESMER;
		case 8:
			return Resources.IMAGE_CLASS_NECROMANCER;
		}
		return null;
	}

	private class updateTimers extends TimerTask {
		public void run() {
			for (Capturepoint cap : capUtil.getCapturepoints(Border.RED)) {
				cap.tickRit(getSizeMultiplier());
			}

			for (Capturepoint cap : capUtil.getCapturepoints(Border.GREEN)) {
				cap.tickRit(getSizeMultiplier());
			}

			for (Capturepoint cap : capUtil.getCapturepoints(Border.BLUE)) {
				cap.tickRit(getSizeMultiplier());
			}

			for (Capturepoint cap : capUtil.getCapturepoints(Border.EB)) {
				cap.tickRit(getSizeMultiplier());
			}

			if (waypointTime > 0) {
				waypointTime--;
			} else {
				((JLabel) waypointTimerPanel.getComponent(0))
						.setIcon(getWaypointIcon());
			}

			((JLabel) waypointTimerPanel.getComponent(1))
					.setText(getTimeAsString(waypointTime));

			updateCapturePoints();
			updatePlayerLocation();
		}
	}

	private double getMapWidth() {
		if (border == Border.EB) {
			return 73728.0;
		}
		return 61440.0;
	}

	private double getMapHeight() {
		if (border == Border.EB) {
			return 73728.0;
		}
		return 86016.0;
	}

	private void updatePlayerLocation() {
		double playerX = (mumbleLink.getfAvatarPosition()[0] * 39.3700787);
		double playerZ = (mumbleLink.getfAvatarPosition()[2] * 39.3700787);

		double distance = Math.sqrt((playerX - 0) * (playerX - 0)
				+ (playerZ - 0) * (playerZ - 0));
		double angle = (float) Math.toDegrees(Math.atan2(0 - playerX,
				0 - playerZ));

		if (angle < 0) {
			angle += 360;
		}

		// System.out.println(angle + " - " + distance + " feet / " + distance
		// / 39.3700787 + " meters");

		String data = mumbleLink.getCharName() + "," + playerX + "," + playerZ
				+ "," + mumbleLink.getMapId() + ","
				+ mumbleLink.getProfession() + "," + guiOptions.getNickname()
				+ "," + guiOptions.getChannel() + "-" + mumbleLink.getWorldId()
				+ "-" + mumbleLink.getTeamColor();
		Socket socket = null;

		try {
			socket = new Socket(Start.ip, 11111);
		} catch (UnknownHostException uhe) {
			// Server Host unreachable
			socket = null;
		} catch (IOException ioe) {
			// Cannot connect to port on given server host
			socket = null;
		}

		if (user32 != null && user32.FindWindow(null, "Guild Wars 2") == null) {
			data = mumbleLink.getCharName() + "," + playerX + "," + playerZ
					+ "," + -1 + "," + mumbleLink.getProfession() + ","
					+ guiOptions.getNickname() + "," + guiOptions.getChannel()
					+ "-" + mumbleLink.getWorldId() + "-"
					+ mumbleLink.getTeamColor();
		}

		if (socket == null || socket.isClosed()) {
			handleLocalLocation(playerX, playerZ);
		} else {
			handleNetworkLocation(socket, data, angle);
		}
	}

	private void handleLocalLocation(double playerX, double playerZ) {
		int locationX = (int) (((getWidth()) / getMapWidth()) * playerX)
				+ (getWidth() / 2);
		int locationZ = (int) (((getHeight()) / getMapHeight()) * (playerZ * -1))
				+ (getHeight() / 2);

		ImageIcon icon = getProfIcon(mumbleLink.getProfession());
		labelPlayer
				.setIcon(new ImageIcon(
						icon.getImage()
								.getScaledInstance(
										(int) (Resources.IMAGE_COMMANDER
												.getIconWidth() * getSizeMultiplier()),
										(int) (Resources.IMAGE_COMMANDER
												.getIconHeight() * getSizeMultiplier()),
										Image.SCALE_SMOOTH)));

		for (String key : players.keySet()) {
			overlayPanel.remove(players.get(key));
		}
		overlayPanel.remove(labelPlayer);
		if (mumbleLink.getMapId() == getBorderId()) {
			overlayPanel.add(labelPlayer);
			overlayPanel.setComponentZOrder(labelPlayer, 0);
			overlayPanelSpringLayout.putConstraint(
					SpringLayout.HORIZONTAL_CENTER, labelPlayer, locationX,
					SpringLayout.WEST, overlayPanel);
			overlayPanelSpringLayout.putConstraint(SpringLayout.NORTH,
					labelPlayer, locationZ, SpringLayout.NORTH, overlayPanel);
		}
		overlayPanel.repaint();
		overlayFrame.repaint();
	}

	private void handleNetworkLocation(Socket socket, String data, double angle) {
		ObjectInputStream in = null;
		PrintWriter out = null;

		try {
			in = new ObjectInputStream(socket.getInputStream());
			out = new PrintWriter(new OutputStreamWriter(
					socket.getOutputStream()));
			out.println(data);
			out.flush();

			try {
				@SuppressWarnings("unchecked")
				HashMap<String, JLabel> playersServer = (HashMap<String, JLabel>) in
						.readObject();
				ArrayList<String> deleteList = new ArrayList<String>();
				for (String key : players.keySet()) {
					if (!playersServer.containsKey(key)) {
						overlayPanel.remove(players.get(key));
						deleteList.add(key);
					}
				}

				for (String key : deleteList) {
					players.remove(key);
				}

				for (String key : playersServer.keySet()) {
					if (!players.containsKey(key)) {
						players.put(key, playersServer.get(key));
					} else {
						players.get(key).setToolTipText(
								playersServer.get(key).getToolTipText());
					}
				}
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
			out.println("Quit");
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				out.close();
				in.close();
				socket.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		overlayPanel.remove(labelPlayer);
		for (String key : players.keySet()) {
			overlayPanel.remove(players.get(key));
			if (Integer
					.parseInt(players.get(key).getToolTipText().split(",")[2]) == getBorderId()) {
				if (players.get(key).getToolTipText().split(",")[5]
						.equals(guiOptions.getChannel() + "-"
								+ mumbleLink.getWorldId() + "-"
								+ mumbleLink.getTeamColor())) {

					double pX = Double.parseDouble(players.get(key)
							.getToolTipText().split(",")[0]);
					double pZ = Double.parseDouble(players.get(key)
							.getToolTipText().split(",")[1]);

					int x = (int) (((getWidth()) / getMapWidth()) * pX)
							+ (getWidth() / 2);
					int z = (int) (((getHeight()) / getMapHeight()) * pZ * -1)
							+ (getHeight() / 2);

					players.get(key).setText(
							players.get(key).getToolTipText().split(",")[4]);

					players.get(key).setFont(
							new Font(Font.SANS_SERIF, Font.BOLD, 10));

					ImageIcon icon = getProfIcon(Integer.parseInt(players
							.get(key).getToolTipText().split(",")[3]));
					// int w = icon.getIconWidth();
					// int h = icon.getIconHeight();
					// int type = BufferedImage.TYPE_INT_ARGB;
					// BufferedImage image = new BufferedImage(h, w, type);
					// Graphics2D g2 = image.createGraphics();
					// double x1 = (h - w) / 2.0;
					// double y1 = (w - h) / 2.0;
					// AffineTransform at =
					// AffineTransform.getTranslateInstance(
					// x1, y1);
					// at.rotate(Math.toRadians(angle), w / 2.0, h / 2.0);
					// g2.drawImage(icon.getImage(), at, players.get(key));
					// g2.dispose();
					// icon = new ImageIcon(image);

					players.get(key)
							.setIcon(
									new ImageIcon(
											icon.getImage()
													.getScaledInstance(
															(int) (Resources.IMAGE_COMMANDER
																	.getIconWidth() * getSizeMultiplier()),
															(int) (Resources.IMAGE_COMMANDER
																	.getIconHeight() * getSizeMultiplier()),
															Image.SCALE_SMOOTH)));

					overlayPanel.add(players.get(key));
					overlayPanel.setComponentZOrder(players.get(key), 0);
					overlayPanelSpringLayout.putConstraint(
							SpringLayout.HORIZONTAL_CENTER, players.get(key),
							x, SpringLayout.WEST, overlayPanel);
					overlayPanelSpringLayout.putConstraint(SpringLayout.NORTH,
							players.get(key), z, SpringLayout.NORTH,
							overlayPanel);
				}
			}
		}

		overlayPanel.repaint();
	}

	private void updateCapturePoints() {
		String currentTimers = "Timers:";
		String timersRed = " Red: ";
		String timersBlue = " | Blue: ";
		String timersGreen = " | Green: ";
		for (Capturepoint cap : capUtil.getCapturepoints(border)) {
			if (cap.getRiTime() > 0 && copyToClipboard) {
				if (cap.getServer() == Capturepoint.RED) {
					timersRed += cap.getChatcode() + " = " + cap.getTimer()
							+ " ";
				} else if (cap.getServer() == Capturepoint.BLUE) {
					timersBlue += cap.getChatcode() + " = " + cap.getTimer()
							+ " ";
				} else if (cap.getServer() == Capturepoint.GREEN) {
					timersGreen += cap.getChatcode() + " = " + cap.getTimer()
							+ " ";
				}
			}
		}
		if (copyToClipboard) {
			if (timersRed.equals(" Red: ")) {
				timersRed += "None";
			}
			if (timersBlue.equals(" | Blue: ")) {
				timersBlue += "None";
			}
			if (timersGreen.equals(" | Green: ")) {
				timersGreen += "None";
			}
			currentTimers += timersRed + timersBlue + timersGreen
					+ " //Powered by: R.I.T.";
			try {
				StringSelection stringSelection = new StringSelection(
						currentTimers);
				Clipboard clipboard = Toolkit.getDefaultToolkit()
						.getSystemClipboard();
				clipboard.setContents(stringSelection, null);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (!overlayFrame.isVisible())
			return;

		updateNextAPICall();

		overlayFrame.setFocusableWindowState(false);
		overlayFrame.setVisible(true);
		overlayFrame.repaint();
	}

	private void updateNextAPICall() {
		Start.nextAPICall--;
		int size = 100 / 15 * Start.nextAPICall;
		nextUpdateBar.setValue(size);
	}

	public boolean isVisible() {
		return overlayFrame.isVisible();
	}

	public void toggleOverlay() {
		overlayFrame.setVisible(!overlayFrame.isVisible());
	}

	public void toggleShowNames() {
		clearOverlayFrame();
		showNames = !showNames;
		updateOverlayFrame();
	}

	public void toggleCopyToClipboard() {
		copyToClipboard = !copyToClipboard;
	}

	public void toggleShowBackground() {
		clearOverlayFrame();
		showBackground = !showBackground;
		updateOverlayFrame();
	}

	public void setBackgroundAlpha(int backgroundTransparency) {
		clearOverlayFrame();
		guiOptions.setBackgroundAlpha(backgroundTransparency);
		updateOverlayFrame();
	}

	private String getTimeAsString(int time) {
		if (time == 0) {
			return "";
		}
		int seconds = time % 60;
		int minutes = time / 60;
		if (seconds < 10) {
			return minutes + ":0" + seconds;
		}
		return minutes + ":" + seconds;
	}

	public void run() {
		while (overlayFrame != null) {
			if (overlayFrame.isVisible()) {

				PointerInfo a = MouseInfo.getPointerInfo();
				Point b = a.getLocation();
				int x = (int) b.getX();
				int y = (int) b.getY();

				isMouseOnOverlay = x > overlayFrame.getLocation().x
						&& x < overlayFrame.getLocation().x + getWidth()
						&& y > overlayFrame.getLocation().y
						&& y < overlayFrame.getLocation().y + getHeight();

				if (isMouseOnOverlay) {
					overlayFrame.setSize(0, 0);
				} else {
					overlayFrame.setSize(getWidth(), getHeight());
				}
			} else {
				try {
					Thread.sleep(100);
				} catch (InterruptedException ignore) {
					ignore.printStackTrace();
				}
			}
		}
	}

}
