package igrek.robopath.modules.whca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedList;
import java.util.List;

import de.felixroske.jfxsupport.FXMLController;
import igrek.robopath.model.Point;
import igrek.robopath.modules.common.ResizableCanvas;
import igrek.robopath.modules.whca.robot.MobileRobot;
import igrek.robopath.pathfinder.mystar.TileMap;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

@FXMLController
public class Presenter {
	
	private Controller controller;
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	private Boolean pressedTransformer;
	final double FPS = 30;
	boolean animating = false;
	int cellSize = 40;
	
	@FXML
	private ResizableCanvas drawArea;
	@FXML
	private VBox drawAreaContainer;
	
	// PARAMS
	@Autowired
	private SimulationParams params;
	
	@FXML
	public TextField paramMapSizeW;
	@FXML
	public TextField paramMapSizeH;
	@FXML
	public TextField paramRobotsCount;
	@FXML
	public CheckBox paramRobotAutoTarget;
	
	
	@Autowired
	public void setController(Controller controller) {
		this.controller = controller;
	}
	
	TileMap getMap() {
		return controller.getMap();
	}
	
	List<MobileRobot> getRobots() {
		return controller.getRobots();
	}
	
	@FXML
	private void resetMap(final Event event) {
		if (event != null)
			params.readFromUI();
		
		controller.resetMap();
		
		if (event != null)
			drawAreaContainerResized();
	}
	
	@FXML
	private void placeRobots(final Event event) {
		if (event != null)
			params.readFromUI();
		controller.placeRobots();
	}
	
	@FXML
	private void generateMaze(final Event event) {
		if (event != null)
			params.readFromUI();
		controller.generateMaze();
	}
	
	private void drawAreaContainerResized() {
		double containerWidth = drawAreaContainer.getWidth();
		double containerHeight = drawAreaContainer.getHeight();
		double maxCellW = containerWidth / getMap().getWidthInTiles();
		double maxCellH = containerHeight / getMap().getHeightInTiles();
		double cellSize = maxCellW < maxCellH ? maxCellW : maxCellH; // min
		drawArea.setWidth(cellSize * getMap().getWidthInTiles());
		drawArea.setHeight(cellSize * getMap().getHeightInTiles());
	}
	
	@FXML
	public void initialize() {
		logger.info("initializing presenter " + this.getClass().getSimpleName());
		
		drawAreaContainer.widthProperty().addListener(o -> drawAreaContainerResized());
		drawAreaContainer.heightProperty().addListener(o -> drawAreaContainerResized());
		
		drawArea.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
			mousePressedMap(event);
		});
		drawArea.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
			mouseDraggedMap(event);
		});
		drawArea.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
		});
		
		params.init(this);
		params.sendToUI();
		repaint();
		startRepaintTimer();
	}
	
	private void startRepaintTimer() {
		// animation timer
		Timeline fiveSecondsWonder = new Timeline(new KeyFrame(Duration.millis(1000 / FPS), new EventHandler<ActionEvent>() {
			
			private long lastTime = System.currentTimeMillis();
			
			@Override
			public void handle(ActionEvent event) {
				long current = System.currentTimeMillis();
				controller.timeLapse(((double) (current - lastTime)) / 1000);
				lastTime = current;
				repaint();
			}
		}));
		fiveSecondsWonder.setCycleCount(Timeline.INDEFINITE);
		fiveSecondsWonder.play();
	}
	
	private void mousePressedMap(MouseEvent event) {
		TileMap map = getMap();
		List<MobileRobot> robots = getRobots();
		
		if (event.getButton() == MouseButton.PRIMARY) {
			
			Point point = locatePoint(map, event);
			if (point != null) {
				Boolean state = map.getCell(point);
				if (!state) {
					MobileRobot occupiedBy = controller.occupiedByRobot(point);
					if (occupiedBy != null) {
						robots.remove(occupiedBy);
					} else {
						controller.createMobileRobot(point, robots.size());
					}
				}
				repaint();
			}
			
		} else if (event.getButton() == MouseButton.SECONDARY) {
			
			Point point = locatePoint(map, event);
			if (point != null) {
				Boolean state = !map.getCell(point);
				map.setCell(point, state);
				pressedTransformer = state;
				repaint();
			}
			
		}
	}
	
	private void mouseDraggedMap(MouseEvent event) {
		if (event.getButton() == MouseButton.SECONDARY) {
			TileMap map = getMap();
			Point point = locatePoint(map, event);
			if (point != null) {
				Boolean state = map.getCell(point);
				if (state != pressedTransformer) {
					map.setCell(point, pressedTransformer);
					repaint();
				}
			}
			
		}
	}
	
	@FXML
	private void randomTargetPressed(final Event event) {
		controller.randomTargetPressed();
	}
	
	@FXML
	private void eventReadParams(final Event event) {
		params.readFromUI();
	}
	
	
	//	VIEW
	void repaint() {
		drawMap();
	}
	
	private void drawMap() {
		GraphicsContext gc = drawArea.getGraphicsContext2D();
		drawGrid(gc);
		drawCells(gc);
		drawRobots(gc);
	}
	
	private void drawCells(GraphicsContext gc) {
		TileMap map = getMap();
		for (int x = 0; x < map.getWidthInTiles(); x++) {
			for (int y = 0; y < map.getHeightInTiles(); y++) {
				Boolean occupied = map.getCell(x, y);
				if (occupied != null) {
					drawCell(gc, x, y, occupied);
				}
			}
		}
	}
	
	private void drawCell(GraphicsContext gc, int x, int y, boolean occupied) {
		TileMap map = getMap();
		double cellW = drawArea.getWidth() / map.getWidthInTiles();
		double cellH = drawArea.getHeight() / map.getHeightInTiles();
		double w2 = 0.9 * cellW;
		double h2 = 0.9 * cellH;
		if (occupied) {
			gc.setFill(Color.rgb(0, 0, 0));
			double x2 = x * cellW + (cellW - w2) / 2;
			double y2 = y * cellH + (cellH - h2) / 2;
			gc.fillRoundRect(x2, y2, w2, h2, w2 / 3, h2 / 3);
		}
	}
	
	private void drawGrid(GraphicsContext gc) {
		TileMap map = getMap();
		gc.clearRect(0, 0, drawArea.getWidth(), drawArea.getHeight());
		
		gc.setLineWidth(1);
		gc.setStroke(Color.rgb(200, 200, 200));
		// vertical lines
		for (int x = 0; x <= map.getWidthInTiles(); x++) {
			double x2 = x * drawArea.getWidth() / map.getWidthInTiles();
			gc.strokeLine(x2, 0, x2, drawArea.getHeight());
		}
		// horizontal lines
		for (int y = 0; y <= map.getHeightInTiles(); y++) {
			double y2 = y * drawArea.getHeight() / map.getHeightInTiles();
			gc.strokeLine(0, y2, drawArea.getWidth(), y2);
		}
	}
	
	public Point locatePoint(TileMap map, MouseEvent event) {
		return locatePoint(map, event.getX(), event.getY());
	}
	
	public Point locatePoint(TileMap map, double screenX, double screenY) {
		int mapX = ((int) (screenX * map.getWidthInTiles() / drawArea.getWidth()));
		int mapY = ((int) (screenY * map.getHeightInTiles() / drawArea.getHeight()));
		if (mapX < 0 || mapY < 0 || mapX >= map.getWidthInTiles() || mapY >= map.getHeightInTiles())
			return null;
		return new Point(mapX, mapY);
	}
	
	private void drawRobots(GraphicsContext gc) {
		List<MobileRobot> robots = getRobots();
		int index = 0;
		for (MobileRobot robot : robots) {
			drawRobot(gc, robot, index++);
		}
	}
	
	private void drawRobot(GraphicsContext gc, MobileRobot robot, int index) {
		TileMap map = getMap();
		double cellW = drawArea.getWidth() / map.getWidthInTiles();
		double cellH = drawArea.getHeight() / map.getHeightInTiles();
		double w = 0.6 * cellW;
		double h = 0.6 * cellH;
		Color robotColor = robotColor(index);
		// draw target
		Point target = robot.getTarget();
		if (target != null && !target.equals(robot.getPosition())) {
			gc.setStroke(robotColor);
			double targetX = target.getX() * cellW + cellW / 2;
			double targetY = target.getY() * cellH + cellH / 2;
			gc.strokeLine(targetX - w / 2, targetY - h / 2, targetX + w / 2, targetY + h / 2);
			gc.strokeLine(targetX - w / 2, targetY + h / 2, targetX + w / 2, targetY - h / 2);
		}
		// draw path
		gc.setStroke(robotColor);
		LinkedList<Point> movesQue = robot.getMovesQue();
		Point previous = robot.getPosition();
		for (Point move : movesQue) {
			double fromX = previous.getX() * cellW + cellW / 2;
			double fromY = previous.getY() * cellH + cellH / 2;
			double toX = move.getX() * cellW + cellW / 2;
			double toY = move.getY() * cellH + cellH / 2;
			gc.strokeLine(fromX, fromY, toX, toY);
			previous = move;
		}
		// draw robot
		gc.setFill(robotColor);
		double x = robot.getInterpolatedX() * cellW + cellW / 2 - w / 2;
		double y = robot.getInterpolatedY() * cellH + cellH / 2 - h / 2;
		gc.fillOval(x, y, w, h);
		// draw its priority
		gc.setFill(robotColor(index, 0.5));
		gc.setTextAlign(TextAlignment.CENTER);
		gc.setTextBaseline(VPos.CENTER);
		gc.setFont(new Font("System", h / 2));
		gc.fillText(Integer.toString(robot.getPriority() + 1), x + w / 2, y + h / 2);
	}
	
	private Color robotColor(int index) {
		return robotColor(index, 1);
	}
	
	private Color robotColor(int index, double b) {
		List<MobileRobot> robots = getRobots();
		double hue = 360.0 * index / robots.size();
		return Color.hsb(hue, 1, b);
	}
	
	@FXML
	private void buttonStep(final Event event) {
		controller.stepTake();
		//		repaint();
	}
	
	@FXML
	private void buttonReset(final Event event) {
		controller.reset();
	}
	
	@FXML
	private void buttonStop(final Event event) {
		animating = false;
	}
}