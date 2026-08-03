import socket
import struct
import threading
import time
from behave import given, when, then
from meshtastic.protobuf import mesh_pb2, portnums_pb2


class TestClient:
    def __init__(self, node_num, port=4403):
        self.node_num = node_num
        self.port = port
        self.socket = None
        self.received_messages = []
        self.running = False
        self.read_thread = None

    def connect(self):
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.socket.connect(("127.0.0.1", self.port))

        to_radio = mesh_pb2.ToRadio()
        to_radio.want_config_id = self.node_num
        self.send_packet(to_radio)

        for _ in range(6):
            payload = self.read_packet()
            if payload is None:
                raise RuntimeError("Connection closed prematurely during handshake")

        self.running = True
        self.read_thread = threading.Thread(target=self.read_loop, daemon=True)
        self.read_thread.start()

    def send_packet(self, to_radio):
        data = to_radio.SerializeToString()
        header = struct.pack(">BBH", 0x94, 0xC3, len(data))
        self.socket.sendall(header + data)

    def read_packet(self):
        try:
            header = self.socket.recv(4)
            if not header or len(header) < 4:
                return None
            if header[0] != 0x94 or header[1] != 0xC3:
                raise ValueError("Invalid magic bytes in header")
            size = struct.unpack(">H", header[2:4])[0]
            payload = b""
            while len(payload) < size:
                chunk = self.socket.recv(size - len(payload))
                if not chunk:
                    break
                payload += chunk
            return payload
        except Exception:
            return None

    def read_loop(self):
        while self.running:
            payload = self.read_packet()
            if payload is None:
                break
            try:
                fr = mesh_pb2.FromRadio()
                fr.ParseFromString(payload)
                if fr.HasField('packet'):
                    packet = fr.packet
                    if packet.HasField('decoded'):
                        decoded = packet.decoded
                        if decoded.portnum == portnums_pb2.PortNum.Value('TEXT_MESSAGE_APP'):
                            msg_text = decoded.payload.decode('utf-8', errors='ignore')
                            sender = getattr(packet, 'from')
                            self.received_messages.append((sender, msg_text))
            except Exception:
                pass

    def send_text_message(self, text, recipient_num):
        to_radio = mesh_pb2.ToRadio()
        p = to_radio.packet
        setattr(p, 'from', self.node_num)
        p.to = recipient_num
        p.channel = 0
        p.decoded.portnum = portnums_pb2.PortNum.Value('TEXT_MESSAGE_APP')
        p.decoded.payload = text.encode('utf-8')
        self.send_packet(to_radio)

    def disconnect(self):
        self.running = False
        if self.socket:
            try:
                self.socket.close()
            except Exception:
                pass


@given("the Meshtastic mock server is running")
def step_impl(context):
    assert context.server_process.poll() is None, "Mock server is not running or failed to start"


@when("Client A connects to the simulator")
def step_impl(context):
    context.client_a = TestClient(node_num=111111)
    context.client_a.connect()


@when("Client B connects to the simulator")
def step_impl(context):
    context.client_b = TestClient(node_num=222222)
    context.client_b.connect()


@when('Client A sends the message "{message}" to Client B\'s node')
def step_impl(context, message):
    context.client_a.send_text_message(message, 222222)


@then('Client B should receive the message "{message}" sent by Client A')
def step_impl(context, message):
    found = False
    for _ in range(30):
        time.sleep(0.1)
        for sender, msg in context.client_b.received_messages:
            if sender == 111111 and msg == message:
                found = True
                break
        if found:
            break
    assert found, f"Client B did not receive message '{message}' sent by Client A"


@when('Client B sends the message "{message}" to Client A\'s node')
def step_impl(context, message):
    context.client_b.send_text_message(message, 111111)


@then('Client A should receive the message "{message}" sent by Client B')
def step_impl(context, message):
    found = False
    for _ in range(30):
        time.sleep(0.1)
        for sender, msg in context.client_a.received_messages:
            if sender == 222222 and msg == message:
                found = True
                break
        if found:
            break
    assert found, f"Client A did not receive message '{message}' sent by Client B"
