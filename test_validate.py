import sys
import os

# add backend/app to path to import models
sys.path.append(os.path.join(os.getcwd(), 'backend', 'app'))
from models import PacketMetadata

try:
    obj = {
      "createdAt": 1712497645,
      "ttl": 3600,
      "maxHops": 10,
      "currentHops": 1,
      "route": [
        {
          "deviceId": "9E26102B",
          "location": {
            "accuracy": 100.0,
            "address": "",
            "lat": 10.841305,
            "lng": 77.2749785
          },
          "timestamp": 1775585022
        }
      ],
      "batteryLevel": 90
    }
    PacketMetadata.model_validate(obj)
    print("SUCCESS")
except Exception as e:
    print("ERROR:")
    print(e)
