"""Default configuration constants for the Meshtastic mock server.

Kept in a dedicated module (SRP) so node identity, channel, and echo
behaviour can be imported by tests without pulling in the full server.
"""

DEFAULT_NODE_NUM = 2345678  # 0x23CB1E in hexadecimal
DEFAULT_LONG_NAME = "Mock LoRa Node"
DEFAULT_SHORT_NAME = "MCK1"
DEFAULT_CHANNEL_NAME = "LongFast"
DEFAULT_PORT = 4403

ECHO_PREFIX = "Received via LoRa Mock: "
