import json
import serial
import arrow
from sklearn.externals import joblib
from flask import Flask, request, jsonify

from predict import predict

app = Flask(__name__)


class StateManager():
    def __init__(self):
        # device_id: str => (probability: float, last_seen: arrow)
        self._devices = {}
        self._timeout = arrow.now()
        self._serial = serial.Serial('/dev/ttyUSB0')

    def get_timeout(self):
        return self._timeout

    def set_timer(self, timer):
        self._timeout = arrow.now().shift(seconds=+timer)
        self._serial.write(f'T{int(timer)}'.encode())

    def _send_prob(self):
        value = 0.0
        for prob, last_seen in self._devices.values():
            # only count phones that checked in in the last 5 minutes
            sec_ago = (arrow.now() - last_seen).total_seconds()
            if sec_ago < 360:
                frac_ago = sec_ago / 360.
                time_factor = max(1. - (frac_ago * frac_ago), 0.5)
                value = max(prob * time_factor, value)
        self._serial.write('D{:.3f}'.format(value).encode())

    def set_device_prob(self, id, prob):
        self._devices[id] = (prob, arrow.now())
        self._send_prob()

state_manager = StateManager()


@app.route('/send-network-scan', methods=['POST'])
def send_network_scan():
    j = json.loads(request.data.decode())
    location = j['location']
    assert location in {'inside', 'outside'}

    data = [[i['bssid'], i['level']] for i in j['wifi']]
    with open(f'data-{location}.xy.jl', 'a') as f:
        json.dump(data, f)
        f.write('\n')

    prob = predict(j['wifi'])
    return 'taken'


@app.route('/status', methods=['GET', 'POST'])
def staus():
    inside_room = None
    if request.method == 'POST':
        j = json.loads(request.data.decode())
        if j['wifi']:
            prob = inside_room = predict(j['wifi'])
            if j['isCharging']:
                prob *= 0.2
            state_manager.set_device_prob(j['deviceID'], prob)

        if j['setTimer'] is not None:
            state_manager.set_timer(j['setTimer'])

    return jsonify(
        insideRoom=inside_room,
        timeout=int(state_manager.get_timeout().timestamp))


if __name__ == '__main__':
    app.run(host='0.0.0.0')
