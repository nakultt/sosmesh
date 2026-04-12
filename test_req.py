import requests

payload = {
  "packet": {
    "id": "test-1234",
    "type": "SOS",
    "senderId": "DEVICE_A",
    "incident": {
      "severity": "CRITICAL",
      "category": "MEDICAL",
      "message": "nah"
    },
    "metadata": {
      "createdAt": 1712497645,
      "ttl": 3600,
      "maxHops": 10,
      "currentHops": 1,
      "route": [],
      "batteryLevel": 90
    },
    "uploaded": False
  },
  "relayDeviceId": "DEVICE_B"
}

r = requests.post("https://sosmesh.onrender.com/api/emergency/sos", json=payload)
print(r.status_code)
print(r.text)
