
import java.util.*;
import java.awt.geom.*;
import java.awt.*;


public class SmartCar {
	public final double CAR_MIN_SPACING = 50;
	public final double THETA_THRESHOLD = .1;
	public final double ROTATION_RATE = 25;
	public final double TURNING_ANGLE_DEGREES = 60;
	public final double ROAD_Y_THRESHOLD = 1;
	public final double STRAIGHTEN_ADJUSTMENT = 0;
	public final double DECEL_RATE = .66;
	public final double CLOSEST_SPEEDER_THRESH = 400;

	// The two controls: either (vel,phi) or (acc,phi)
	double acc;       // Acceleration.
	double vel;       // Velocity.
	double phi;       // Steering angle.
	double x;       //X coordinate
	double y;       //Y coordinate
	double theta;
	int lane;
	int newlane;
	double targetVel; //Velocity car wants to maintain
	double distMoved;
	boolean changingLanes;
	boolean isStraightening;
	Road road;
	boolean isSpeeder;
	boolean isGettingSpeeder;

	double width = 34;
	double height = 16;

	int color = 1;
	int numCarColors= 4;

	boolean DEBUG;

	ArrayList<Rectangle2D.Double> obstacles;
	//    SensorPack sensors;

	// Is the first control an accelerator?
	boolean isAccelModel = false;

	UniformRandom random = new UniformRandom();

	/**
	 * [init description]
	 * @param initX     [description]
	 * @param initY     [description]
	 * @param initTheta [description]
	 * @param endX      [description]
	 * @param endY      [description]
	 * @param endTheta  [description]
	 * @param obstacles [description]
	 * @param sensors   [description]
	 */
	public SmartCar (int lane, double initTheta, double startSpeed, Road thisRoad, boolean debug, boolean isSpeeder, int numCarColors) {
		//        this.obstacles = obstacles;
		//        this.sensors = sensors;
		//        this.lane = lane;
		this.vel = this.targetVel = startSpeed;
		this.phi = 0;
		this.x = -width;
		this.theta = initTheta;
		this.lane = lane;
		this.road = thisRoad;
		this.y = road.getLaneCenter(lane);

		this.isStraightening = false;
		this.changingLanes = false;
		this.isGettingSpeeder = false;
		this.isSpeeder = isSpeeder;
		this.numCarColors = numCarColors;
		this.DEBUG = debug;
		distMoved = 0.0;

		this.color = UniformRandom.uniform(0,numCarColors-1);

	}

	/**
	 * [getControl description]
	 * @param  i [description]
	 * @return   [description]
	 */
	public double getControl (int i) {
		if (i == 1) {
			if (isAccelModel) {
				return acc;
			} else {
				return vel;
			}
		} else if (i == 2) {
			return phi;
		}
		return 0;
	}


	public void move () {
		if (changingLanes) checkChangingLanes();
		else if (isStraightening) {
			//If straightened out, stop rotating
			if (straightenedOut()) {
				phi = 0;
				isStraightening = false;
			}
		}
		//If not changing lanes or straightening, do logic
		else {
			phi = 0;

			if (isSpeeder){}
			else{}
		}

		//Finally, check if about to hit something
		if (tooCloseToCar()) {
			//Speeder will first try to change lanes
			if (this.isSpeeder) {
				if (!changeLanes(true)) {
					if (!changeLanes(false)) {
						//If unable to change lanes (both left and right return false), slow down
						vel = vel * DECEL_RATE;
					}
				}
			}

			else {
				//If not speeder, just slow down if near car
				if (DEBUG) System.out.println("Law abider slowing down");
				vel = vel * DECEL_RATE;
			}
		}
		//If not about to hit car, speeder return to targetVelocity
		else vel = targetVel;

		//Catch speeder logic
		if(!isSpeeder) {
			//Step 1: See if there is a speeder behind you, get closest
			SmartCar speeder = findSpeeder();
			//if there is a speeder, slow down until you are directly in front
			if (speeder != null) {
				isGettingSpeeder = true;
				//Slow down until car is in front of speeder, anyLane = true;
				if (!frontOfSpeeder(speeder, true)) {
					vel = vel*DECEL_RATE;
					if (DEBUG) System.out.println("Slowing down to get speeder at " + road.getX(speeder));
				}
				else if (!frontOfCar()) {
					//If in front of another car, try to change lanes toward speeder.
					int targetLane = road.openLaneforSpeeder(speeder);
					if (targetLane == 0) {
						//If lane is 0, all lanes covered. Need to slow down
						if(DEBUG) System.out.println("All lanes in front of speeder taken");
						vel = vel * DECEL_RATE;
					}
					else if (targetLane > lane) {
						if (!changeLanes(false)) {
							vel = vel *DECEL_RATE;
							if(DEBUG) System.out.println("Cant change lanes right to get speeder") ;
						}
					}
					else {
						if (!changeLanes(true)) {
							vel = vel *DECEL_RATE;
							if (DEBUG) System.out.println("Cant change lanes left to get speeder") ;
						}
					}
				}
				//Then go the speed limit.
				else vel = road.speedLimit;


				//				//Step 2: If speeder, see if there is an open lane

			}
			//else if no speeder, return to target speed
			else {
				isGettingSpeeder = false;
				if(vel > targetVel) slowDown();
				else if (vel < targetVel) speedUp();
			}
		}//End catch speeder


		//
		// logic:
		//
		// am i about to hit a car
		// should i change lanes?
		// tooCloseToCar()
		//
		//if any car is speeding and not i'm changing lanes
		//is speeder in my lane
		//are/is other car(s) in the other lane(s)?
		//
		//do i need to change lanes to stop speeder?
		//
		//}

	}

	//Check if car is in front of any car
	private boolean frontOfCar() {
		//For each car, return true if in front and in same lane
		for (SmartCar c: road.getCars()) {
			if (!c.equals(this) && (Math.abs(this.x -(road.getX(c) + width)) < CAR_MIN_SPACING) && (road.getLane(c) == this.lane)) return true;
		}
		//If no car returned true, return false
		return false;
	}

	//Check if in front of speeder. If anyLane = false, speeder must be in same lane. Otherwise any lane
	private boolean frontOfSpeeder(SmartCar speeder, boolean anyLane) {
		if (Math.abs(this.x -(road.getX(speeder) + width)) < CAR_MIN_SPACING) return (anyLane || (road.getLane(speeder) == this.lane));
		else return false;
	}

	/**
	 * changes lanes for car
	 * returns false if not possible (or fails)
	 * updates lane value
	 * @return [description]
	 */
	public boolean tooCloseToCar() {
		ArrayList<SmartCar> cars = road.getCars();
		for (SmartCar c : cars) {
			//Car ignores itself
			if (!c.equals(this)) {
				//Check if in the same lane
				if ((this.lane == road.getLane(c)) || ((this.newlane == road.getLane(c)) && this.changingLanes)|| (this.lane == road.getNewLane(c))) {
					//Check if car is in front and too close
					if ((road.getX(c) > this.x) && ((road.getX(c) - this.x) < CAR_MIN_SPACING)) {
						if (DEBUG) System.out.println("Car detected in front");
						return true;
					}
				}
			}
		}
		return false;

		//change lanes if necessary
	}

	public void draw (Graphics2D g2, Dimension D) {
		// If you want do draw something on the screen (e.g., a path)
		// this is where you do it. Remember to convert to Java coordinates:
		//    yJava = D.height - y;
		//    paramGraphics2D.setColor(Color.cyan);
	}

	/**
	 * changes lanes for car
	 * returns false if not possible (or fails)
	 * updates lane value
	 * @return [description]
	 */
	public boolean changeLanes(boolean left) {
		int deltaLane = -1; //If turning left, decrease lane
		if(!left) deltaLane = 1; //If right, increase lane

		if (!changingLanes && !isStraightening) {
			if (lane == 1 && left) {
				if (DEBUG) System.out.println("Cannot change lanes left");
				return false;
			}
			else if (lane == road.numLanes && !left) {
				if (DEBUG) System.out.println("Cannot change lanes right");
				return false;
			}

			else {
				//Check if there is a car in the next lane					
				for (SmartCar c: road.getCars()) {
					//Ignore self
					if(!c.equals(this)) {
						//If car from list is in the lane trying to change to
						if(road.getLane(c)==this.lane+deltaLane) {
							//Return false if in the range of X coordinates
							if((road.getX(c) > this.x-1.5*width) && road.getX(c) < this.x+1.5*width) {
								if(DEBUG) System.out.println("Cannot change lanes left due to car occupying space");
								return false;
							}
						}
					}
				}

				//If function hasn't returned false by this point, lane change is possible

				newlane = lane + deltaLane;
				this.phi = -1 * deltaLane * ROTATION_RATE;
				changingLanes = true;
				return true;
			}
		}

		//If currently turning or straightening, unable to change lanes
		if (DEBUG) System.out.println("Unable to change lanes: Car is currently changing or straightening");
		return false;
	}

	public void checkChangingLanes() {
		if (this.changingLanes) {
			double angleDist = Math.abs(this.theta - TURNING_ANGLE_DEGREES * Math.PI / 180);
			double secondDist = Math.abs((Math.PI * 2 - this.theta) + TURNING_ANGLE_DEGREES * Math.PI / 180);
			if (secondDist < angleDist) angleDist = secondDist;


			//If at turning angle, set phi to zero
			if (angleDist > THETA_THRESHOLD) {
				phi = 0;
			}

			//If at y, rotate back to theta = 0;
			if (Math.abs(this.y - road.getLaneCenter(newlane) - STRAIGHTEN_ADJUSTMENT) < ROAD_Y_THRESHOLD) {
				isStraightening = true;
				changingLanes = false;
				this.lane = newlane;

				//Find shortest rotation back to zero (will be the reverse direction it rotated to turn)
				if (theta > Math.PI) phi = ROTATION_RATE;
				else phi = -ROTATION_RATE;
			}


			//set phi/vel
			//if y val is target lane y
			//straighten out
			//if straightened out
			//changing lanes = false


		}
		return;
	}

	public boolean straightenedOut() {
		if ((this.theta < THETA_THRESHOLD) || (2 * Math.PI - this.theta < THETA_THRESHOLD)) return true;
		else return false;
	}

	/**
	 * adjust to speed to parameter
	 * @param v velocity to change to
	 */
	public void adjustSpeed(double v) {
		if (DEBUG) System.out.println("Velocity changing to: " + v * 10 + " mph");
		this.vel = v;
	}

	private SmartCar findSpeeder(){
		SmartCar closest = null;
		for (SmartCar c: road.cars) {
			//Check if car is a speeder and if behind me
			if(c.isSpeeder && (road.getX(c) < (this.x + width))) {
				//Check is this speeder is closer then current closest
				if((closest == null)|| road.getX(c) < road.getX(closest)) closest = c;
			}
		}
		if ((this.x - road.getX(closest)) < CLOSEST_SPEEDER_THRESH) return closest;
		else return null;
	}

	private void slowDown(){
		vel = vel*DECEL_RATE;
	}

	private void speedUp(){
		vel = vel*(2-DECEL_RATE);

	}



}