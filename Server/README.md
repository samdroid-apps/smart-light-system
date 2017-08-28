# Computer <> Arduino interface

Does a lot of helpful things:

* Provides a webserver for apps to set timers, etc.
* Handles machine learning for inside/out-of room detection
* Sends probabilities to the arduino

## Running

Requires python3.  Probably you want to install:

* Numpy (through your package manager - slow to compile)
* `pip3 install flask arrow scikit-learn`

Run web server:

    python3 main.py

To train the ML-model (after you collected data using the app):

    python3 learn.py

Make sure to restart the server if you retrain the model.
