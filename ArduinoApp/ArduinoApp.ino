#include <NewPing.h>
#define u8  unsigned char
#define u16 unsigned int
#define u32 unsigned long
     
#define TRIGGER_PIN  3
#define ECHO_PIN     2
#define MAX_DISTANCE 200
// must be a PWM pin (with the "~" printed on the board)
#define LED_PIN      11
NewPing sonar(TRIGGER_PIN, ECHO_PIN, MAX_DISTANCE);

// the sonar reads the door frame as 70cm away, but we add a little bit of a buffer (as the door swings)
#define DOOR_FRAME_DISTANCE (70 - 5)
// if the door is occupied for 2 times inside this timeframe, the events are debounced into one
#define DOOR_DEBOUNCE_TIME 2500
// timeout to reset # of people inside room
#define ROOM_EMPTY_TIME 10000



void setup() {
  Serial.begin(9600);
  pinMode(LED_PIN, OUTPUT);
}

u32 total_readings = 0;
u16 n_readings = 0;
u16 average_reading() {
  // returns a reading in cm; or -1 if we are not ready yet as to not block the mainthread for a long time
  u16 reading = sonar.ping_cm();
  if (reading != 0) {
    // 0 is useless - it is returned for both very close and very far away
    // should just disregard this.  Due to the doorframe, you should never
    // a zero
    total_readings += sonar.ping_cm();
    n_readings++; 
  }

  if (n_readings == 16) {
    u16 ret = (u16) (total_readings / ((u32) n_readings));
    n_readings = 0;
    total_readings = 0;
    return ret;
  }
  
  return -1;
}

// maintain 2 part light state for smooth transitions
u8 light_brightness = 0;
u8 light_brightness_desired = 255;
u32 brightness_last_set = 0;

// maintain confidence values (float)
float prob_device = 0.0f;
u8 n_people_inside = 0;
u16 n_walk_throughs = 0;

u32 door_last_occupied = 0;

// time (millis) when the light should turn off
u32 timeout = 0;
// used by app to turn off light
bool override_turn_off = false;

void reaclculate_state() {
  // call this function when the probablities have been changed
  // this recalculated the desired brightnesses
  float prob_sonar = ((float) n_people_inside) * 0.8;
  float prob_overall = prob_device + prob_sonar;
  if (prob_overall > 1.) {
    prob_overall = 1.;
  }

  if (millis() < timeout) {
    prob_overall = 1.;
  }
  if (override_turn_off) {
    prob_overall = 0.;
  }
  light_brightness_desired = (u8) (prob_overall * 255.);
}

void doorWalkedThrough() {
  override_turn_off = false;
  n_walk_throughs++;
  if (n_people_inside) {
    n_people_inside--;
  } else {
    n_people_inside++;
  }
}

void loop() {
  ////////////////////
  // Set Brightness //
  ////////////////////
  reaclculate_state();
  if (light_brightness < light_brightness_desired) {
    // light turns up the brightness quite quickly
    light_brightness += min(10, light_brightness_desired-light_brightness);
    brightness_last_set = millis();
  }
  if (light_brightness > light_brightness_desired) {
    if (override_turn_off) {
      // go down fast
      light_brightness -= min(10, light_brightness-light_brightness_desired);
    } else {
      light_brightness--;
    }
    brightness_last_set = millis();
  }
  // analogWirite uses PWM to scale the brightness from 0 to 255
  analogWrite(LED_PIN, light_brightness);
  delay(10);

  
  /////////////////////////
  // Communicate via USB //
  /////////////////////////
  if (Serial.available() > 0) {
    byte cmd = Serial.read();
    if (cmd == 'D') {
      // Set the prob_device value
      prob_device = Serial.parseFloat();
    }
    if (cmd == 'T') {
      // Set the timer value in seconds
      u32 timer_length = Serial.parseInt();
      // note, millis overflows after 50 days - should handle this case gracefully (or maybe reboot every night)
      timeout = millis() + timer_length*1000;

      override_turn_off = timer_length == 0;

      if (timer_length != 0) {
        override_turn_off = false;
        if (n_people_inside == 0) {
          // when 2 people walk in, it gets recorded as an in, the out
          // so when it is manually turned on; then we assumed "oh 2 people walked in"
          n_people_inside = n_walk_throughs;
        }
      }
    }
  }

  ////////////////////////
  // Manage Sonar State //
  ////////////////////////
  if (millis() - brightness_last_set > ROOM_EMPTY_TIME && light_brightness == 0) {
    brightness_last_set = millis();
    // reset the walkthrougs counter if the room has been empty for a while
    n_walk_throughs = 0;
  }
  u16 cm = average_reading();
  if (cm != -1) {
    if (cm < DOOR_FRAME_DISTANCE) {
      if (millis() - door_last_occupied > DOOR_DEBOUNCE_TIME) {
         doorWalkedThrough();
         door_last_occupied = millis();
      }
    }
  }
}

