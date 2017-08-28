from pprint import pprint  # noqa
import json

from sklearn.linear_model import LinearRegression
from sklearn.naive_bayes import BernoulliNB
from sklearn.tree import DecisionTreeRegressor
from sklearn.cross_validation import train_test_split
from sklearn.externals import joblib
import numpy as np


INSIDE_FP = 'data-inside.xy.jl'
OUTSIDE_FP = 'data-outside.xy.jl'

# -100 is an "unusable" RSSI, so -150 should be terrible
# http://www.metageek.com/training/resources/understanding-rssi.html
NOT_FOUND_RSSI = -150


def get_all_bssids(fp):
    ret = set()
    with open(fp) as f:
        for line in f:
            data = json.loads(line)
            for bssid, _ in data:
                ret.add(bssid)
    return ret


def get_samples_from_file(fp):
    ret = []
    with open(fp) as f:
        for line in f:
            data = [NOT_FOUND_RSSI] * len(bssid_to_index)
            j = json.loads(line)
            for bssid, level in j:
                data[bssid_to_index[bssid]] = level
            ret.append(data)
    return ret

if __name__ == '__main__':
    all_bssids = list(get_all_bssids(INSIDE_FP).union(get_all_bssids(OUTSIDE_FP)))
    bssid_to_index = {v: i for i, v in enumerate(all_bssids)}
    print('# of Bssids:', len(all_bssids))

    inside_samples = get_samples_from_file(INSIDE_FP)
    outside_samples = get_samples_from_file(OUTSIDE_FP)
    X = np.concatenate((inside_samples, outside_samples))
    print('X shape', X.shape)
    Y = np.concatenate((
        np.ones(len(inside_samples)),
        np.zeros(len(outside_samples))))
    print('Y shape', Y.shape)
    X_train, X_test, Y_train, Y_test = train_test_split(X, Y, test_size=.2, random_state=42)

    model = DecisionTreeRegressor()
    model.fit(X_train, Y_train)
    print('Score on train:', model.score(X_train, Y_train))
    print('Score on train:', model.score(X_train, Y_train))

    total_diff = 0
    for X_example, Y_example in zip(X_test, Y_test):
        # print('Example:')
        # for bssid, level in zip(all_bssids, X_example):
        #     print('    ', bssid, level)
        # print('==>', model.predict([X_example])[0])
        # print('real was', Y_example)
        p = model.predict([X_example])[0]
        total_diff += abs(p - Y_example)
    print(total_diff, total_diff / len(X_example))

    joblib.dump(model, 'model.pkl')
    with open('model.bssids.json', 'w') as f:
        json.dump(bssid_to_index, f)
