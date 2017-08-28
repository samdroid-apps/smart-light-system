import os
import json
from sklearn.externals import joblib
import typing as T

from learn import NOT_FOUND_RSSI

if os.path.isfile('model.pkl') and os.path.isfile('model.bssids.json'):
    model = joblib.load('model.pkl')
    with open('model.bssids.json') as f:
        bssid_to_index = json.load(f)
else:
    model = None


def predict(data_list: T.List[dict]) -> float:
    # 1 = inside
    # 0 = outside
    if model is None:
        return 0.5

    x = [NOT_FOUND_RSSI] * len(bssid_to_index)
    for item in data_list:
        bssid = item['bssid']
        level = item['level']
        if bssid in bssid_to_index:
            x[bssid_to_index[bssid]] = level
    X = [x]
    Y = model.predict(X)
    return Y[0]
