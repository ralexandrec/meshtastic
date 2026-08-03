#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import asyncio
import struct
import time
import argparse
import sys
from datetime import datetime
from typing import Set, Dict, List, Optional, Any

from meshtastic.protobuf import mesh_pb2, config_pb2, channel_pb2, portnums_pb2

from config import (
    DEFAULT_NODE_NUM,
    DEFAULT_LONG_NAME,
    DEFAULT_SHORT_NAME,
    DEFAULT_CHANNEL_NAME,
    DEFAULT_PORT,
    ECHO_PREFIX,
)

# Try to import bless and its types at the top to avoid NameError issues
try:
    from bless import (
        BlessServer,
        BlessGATTCharacteristic,
        GATTCharacteristicProperties,
        GATTAttributePermissions
    )
    BLE_AVAILABLE = True
except ImportError:
    BLE_AVAILABLE = False
    # Dummy classes to avoid NameErrors in type hints if bless is not installed
    class BlessServer: pass
    class BlessGATTCharacteristic: pass
    class GATTCharacteristicProperties: pass
    class GATTAttributePermissions: pass

# Definition of official Meshtastic BLE UUIDs
OFFICIAL_SERVICE_UUID = "6ba1b218-15a8-461f-9fa8-5dcae273eafd"
TORADIO_UUID = "f75c76d2-129e-4dad-a1dd-7866124401e7"
FROMRADIO_UUID = "2c55e69e-4993-11ed-b878-0242ac120002"
FROMNUM_UUID = "ed9da18c-a800-4f66-a670-aa7547e34453"

# Logs Configuration
def log(msg: str):
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
    print(f"[{timestamp}] {msg}")

class MeshtasticMockNode:
    def __init__(self, node_num: int, long_name: str, short_name: str, region: int, channel_name: str, enable_echo: bool = True):
        self.node_num = node_num
        self.long_name = long_name
        self.short_name = short_name
        self.region = region
        self.channel_name = channel_name
        self.reboot_count = 1
        self.enable_echo = enable_echo
        
        # Pool of active TCP connections
        self.tcp_clients: Set['TCPConnectionHandler'] = set()
        
        # BLE output buffer (Queue of serialized FromRadio packets)
        self.ble_outbox: List[bytes] = []
        self.ble_counter = 0

    def build_my_info(self) -> mesh_pb2.FromRadio:
        fr = mesh_pb2.FromRadio()
        fr.my_info.my_node_num = self.node_num
        fr.my_info.reboot_count = self.reboot_count
        return fr

    def build_metadata(self) -> mesh_pb2.FromRadio:
        fr = mesh_pb2.FromRadio()
        fr.metadata.firmware_version = "2.5.0"
        fr.metadata.role = config_pb2.Config.DeviceConfig.Role.Value('CLIENT')
        fr.metadata.hw_model = mesh_pb2.HardwareModel.Value('HELTEC_V3')
        return fr

    def build_node_info(self) -> mesh_pb2.FromRadio:
        fr = mesh_pb2.FromRadio()
        ni = fr.node_info
        ni.num = self.node_num
        ni.user.id = f"!{self.node_num:08x}"
        ni.user.long_name = self.long_name
        ni.user.short_name = self.short_name
        # MAC address based on NodeNum (last 6 bytes)
        ni.user.macaddr = struct.pack(">Q", self.node_num)[2:]
        ni.user.hw_model = mesh_pb2.HardwareModel.Value('HELTEC_V3')
        
        # Fixed location (São Paulo / Paulista Ave.)
        ni.position.latitude_i = -235615000  # -23.5615
        ni.position.longitude_i = -466560000 # -46.6560
        ni.position.altitude = 760
        return fr

    def build_config(self) -> mesh_pb2.FromRadio:
        fr = mesh_pb2.FromRadio()
        c = fr.config
        c.lora.region = self.region
        c.lora.modem_preset = config_pb2.Config.LoRaConfig.ModemPreset.Value('LONG_FAST')
        c.device.role = config_pb2.Config.DeviceConfig.Role.Value('CLIENT')
        return fr

    def build_channel(self) -> mesh_pb2.FromRadio:
        fr = mesh_pb2.FromRadio()
        ch = fr.channel
        ch.index = 0
        ch.role = channel_pb2.Channel.Role.Value('PRIMARY')
        ch.settings.name = self.channel_name
        ch.settings.psk = b'\x01'  # Default PSK for public channels (LongFast)
        return fr

    def build_config_complete(self, config_id: int) -> mesh_pb2.FromRadio:
        fr = mesh_pb2.FromRadio()
        fr.config_complete_id = config_id
        return fr

    def build_text_message(self, text: str, sender_num: int, recipient_num: int) -> mesh_pb2.FromRadio:
        fr = mesh_pb2.FromRadio()
        p = fr.packet
        setattr(p, 'from', sender_num)
        p.to = recipient_num
        p.channel = 0
        p.id = int(time.time()) & 0xFFFFFFFF
        p.decoded.portnum = portnums_pb2.PortNum.Value('TEXT_MESSAGE_APP')
        p.decoded.payload = text.encode('utf-8')
        return fr


class TCPConnectionHandler:
    def __init__(self, node: MeshtasticMockNode, reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
        self.node = node
        self.reader = reader
        self.writer = writer
        self.send_queue = asyncio.Queue()
        self.peername = writer.get_extra_info('peername')

    async def run(self):
        log(f"[TCP] New connection from {self.peername}")
        self.node.tcp_clients.add(self)
        write_task = asyncio.create_task(self.write_loop())
        try:
            await self.read_loop()
        except asyncio.IncompleteReadError:
            log(f"[TCP] Connection terminated by client at {self.peername}")
        except Exception as e:
            log(f"[TCP ERROR] Connection error with {self.peername}: {e}")
        finally:
            self.node.tcp_clients.discard(self)
            write_task.cancel()
            self.writer.close()
            try:
                await self.writer.wait_closed()
            except Exception:
                pass
            log(f"[TCP] Connection closed with {self.peername}")

    async def read_loop(self):
        while True:
            # 1. Read 4 header bytes
            header = await self.reader.readexactly(4)
            if not header:
                break
            
            # Magic bytes validation
            if header[0] != 0x94 or header[1] != 0xC3:
                log(f"[TCP WARNING] Invalid header received from {self.peername}: {header.hex()}")
                # Discards bytes until synced or closed
                continue
            
            # Extracts size (Big-Endian uint16)
            payload_len = struct.unpack(">H", header[2:4])[0]
            
            # 2. Read protobuf payload
            payload = await self.reader.readexactly(payload_len)
            
            # 3. Decode ToRadio
            to_radio = mesh_pb2.ToRadio()
            try:
                to_radio.ParseFromString(payload)
                await self.handle_to_radio(to_radio)
            except Exception as e:
                log(f"[TCP ERROR] Failed to decode ToRadio from {self.peername}: {e}")

    async def write_loop(self):
        try:
            while True:
                from_radio = await self.send_queue.get()
                payload_bytes = from_radio.SerializeToString()
                # Build header: 0x94, 0xC3, length (Big-Endian uint16)
                header = struct.pack(">BBH", 0x94, 0xC3, len(payload_bytes))
                self.writer.write(header + payload_bytes)
                await self.writer.drain()
                self.send_queue.task_done()
        except asyncio.CancelledError:
            pass

    async def handle_to_radio(self, to_radio: mesh_pb2.ToRadio):
        # 1. Handle Handshake (config request)
        if to_radio.want_config_id:
            config_id = to_radio.want_config_id
            log(f"[TCP] Client {self.peername} requested handshake (want_config_id: {config_id})")
            
            # Send node sync sequence
            await self.send_queue.put(self.node.build_my_info())
            await self.send_queue.put(self.node.build_metadata())
            await self.send_queue.put(self.node.build_node_info())
            await self.send_queue.put(self.node.build_config())
            await self.send_queue.put(self.node.build_channel())
            await self.send_queue.put(self.node.build_config_complete(config_id))
            log(f"[TCP] Sent complete 6-packet handshake to {self.peername}")
            return

        # 2. Handle packet reception
        if to_radio.HasField('packet'):
            packet = to_radio.packet
            sender_num = getattr(packet, 'from')
            sender_id = f"!{sender_num:08x}" if sender_num else "Unknown"
            
            # Forward the packet (Broadcast/Relay) to other connected TCP clients
            from_radio = mesh_pb2.FromRadio()
            from_radio.packet.CopyFrom(packet)
            for client in list(self.node.tcp_clients):
                if client != self:
                    log(f"[TCP Relay] Relaying packet from {sender_id} to {client.peername}")
                    await client.send_queue.put(from_radio)
            
            if packet.HasField('decoded'):
                decoded = packet.decoded
                if decoded.portnum == portnums_pb2.PortNum.Value('TEXT_MESSAGE_APP'):
                    message_text = decoded.payload.decode('utf-8', errors='ignore')
                    log(f"[TCP] Received from {sender_id}: \"{message_text}\"")
                    
                    # Start async Echo Bot behavior if active
                    if self.node.enable_echo:
                        asyncio.create_task(self.trigger_echo_bot(message_text, sender_num))

    async def trigger_echo_bot(self, original_text: str, client_num: int):
        # Simulated LoRa latency of 1 second
        await asyncio.sleep(1.0)
        
        echo_text = f"{ECHO_PREFIX}{original_text}"
        log(f"[TCP Bot] Replying to !{client_num:08x}: \"{echo_text}\"")
        
        # Send response
        from_radio = self.node.build_text_message(echo_text, self.node.node_num, client_num)
        await self.send_queue.put(from_radio)


class BLEServerManager:
    def __init__(self, node: MeshtasticMockNode, service_uuid: str):
        self.node = node
        self.service_uuid = service_uuid
        self.server = None
        self.loop = asyncio.get_running_loop()

    async def start(self):
        if not BLE_AVAILABLE:
            raise RuntimeError("Library 'bless' is not available.")
            
        self.server = BlessServer(name="Meshtastic Mock Server", loop=self.loop)
        self.server.read_request_func = self.read_request
        self.server.write_request_func = self.write_request

        # Register Services and Characteristics
        await self.server.add_new_service(self.service_uuid)

        # TORADIO (Write)
        char_toradio = BlessGATTCharacteristic(
            uuid=TORADIO_UUID,
            properties=GATTCharacteristicProperties.write | GATTCharacteristicProperties.write_without_response,
            permissions=GATTAttributePermissions.writable,
            value=bytearray()
        )
        await self.server.add_new_characteristic(self.service_uuid, char_toradio)

        # FROMRADIO (Read)
        char_fromradio = BlessGATTCharacteristic(
            uuid=FROMRADIO_UUID,
            properties=GATTCharacteristicProperties.read,
            permissions=GATTAttributePermissions.readable,
            value=bytearray()
        )
        await self.server.add_new_characteristic(self.service_uuid, char_fromradio)

        # FROMNUM (Read | Notify)
        char_fromnum = BlessGATTCharacteristic(
            uuid=FROMNUM_UUID,
            properties=GATTCharacteristicProperties.read | GATTCharacteristicProperties.notify,
            permissions=GATTAttributePermissions.readable,
            value=bytearray(struct.pack('<I', 0))
        )
        await self.server.add_new_characteristic(self.service_uuid, char_fromnum)

        await self.server.start()
        log(f"[BLE] GATT Server started with Service UUID: {self.service_uuid}")

    def read_request(self, characteristic: BlessGATTCharacteristic, **kwargs) -> bytearray:
        uuid_str = characteristic.uuid.lower()
        
        # 1. Read FROMRADIO (consumes queue)
        if uuid_str == FROMRADIO_UUID:
            if self.node.ble_outbox:
                packet = self.node.ble_outbox.pop(0)
                log(f"[BLE] Client consumed FROMRADIO packet ({len(packet)} bytes). Remaining in queue: {len(self.node.ble_outbox)}")
                return bytearray(packet)
            else:
                return bytearray()  # Empty queue
        
        # 2. Read FROMNUM (packet counter)
        elif uuid_str == FROMNUM_UUID:
            val = struct.pack('<I', self.node.ble_counter)
            return bytearray(val)

        return characteristic.value

    def write_request(self, characteristic: BlessGATTCharacteristic, value: Any, **kwargs):
        characteristic.value = value
        uuid_str = characteristic.uuid.lower()
        
        # 1. Write to TORADIO
        if uuid_str == TORADIO_UUID:
            data = bytes(value)
            log(f"[BLE] Write of {len(data)} bytes received on TORADIO")
            # Safely execute BLE thread processing in the async loop
            asyncio.run_coroutine_threadsafe(self.process_to_radio(data), self.loop)

    async def process_to_radio(self, data: bytes):
        try:
            to_radio = mesh_pb2.ToRadio()
            to_radio.ParseFromString(data)
            
            # 1. Handle Handshake
            if to_radio.want_config_id:
                config_id = to_radio.want_config_id
                log(f"[BLE] Client requested handshake via BLE (want_config_id: {config_id})")
                
                await self.enqueue_ble_packet(self.node.build_my_info())
                await self.enqueue_ble_packet(self.node.build_metadata())
                await self.enqueue_ble_packet(self.node.build_node_info())
                await self.enqueue_ble_packet(self.node.build_config())
                await self.enqueue_ble_packet(self.node.build_channel())
                await self.enqueue_ble_packet(self.node.build_config_complete(config_id))
                log(f"[BLE] Complete 6-packet handshake enqueued for sending.")
                return

            # 2. Handle Text Message
            if to_radio.HasField('packet'):
                packet = to_radio.packet
                sender_num = getattr(packet, 'from')
                sender_id = f"!{sender_num:08x}" if sender_num else "Unknown"
                
                if packet.HasField('decoded'):
                    decoded = packet.decoded
                    if decoded.portnum == portnums_pb2.PortNum.Value('TEXT_MESSAGE_APP'):
                        message_text = decoded.payload.decode('utf-8', errors='ignore')
                        log(f"[BLE] Received from {sender_id}: \"{message_text}\"")
                        
                        # Trigger Echo Bot if active
                        if self.node.enable_echo:
                            asyncio.create_task(self.trigger_echo_bot(message_text, sender_num))

        except Exception as e:
            log(f"[BLE ERROR] Failed to handle ToRadio: {e}")

    async def trigger_echo_bot(self, original_text: str, client_num: int):
        # LoRa latency of 1 second
        await asyncio.sleep(1.0)
        
        echo_text = f"{ECHO_PREFIX}{original_text}"
        log(f"[BLE Bot] Replying to !{client_num:08x}: \"{echo_text}\"")
        
        from_radio = self.node.build_text_message(echo_text, self.node.node_num, client_num)
        await self.enqueue_ble_packet(from_radio)

    async def enqueue_ble_packet(self, from_radio: mesh_pb2.FromRadio):
        data = from_radio.SerializeToString()
        self.node.ble_outbox.append(data)
        self.node.ble_counter += 1
        
        # Update counter in FROMNUM and send BLE Notification
        char_fromnum = self.server.get_characteristic(FROMNUM_UUID)
        if char_fromnum:
            char_fromnum.value = bytearray(struct.pack('<I', self.node.ble_counter))
            await self.server.update_value(self.service_uuid, FROMNUM_UUID)


async def main():
    parser = argparse.ArgumentParser(description="Python Meshtastic Mock Server for macOS")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help="TCP port of the server (default: 4403)")
    parser.add_argument("--region", type=str, choices=["US", "LORA_24"], default="LORA_24", 
                        help="Simulated LoRa region (default: LORA_24)")
    parser.add_argument("--service-uuid", type=str, default=OFFICIAL_SERVICE_UUID, 
                        help=f"Service UUID for BLE (official default: {OFFICIAL_SERVICE_UUID})")
    parser.add_argument("--no-ble", action="store_true", help="Completely disables BLE simulation")
    parser.add_argument("--no-echo", action="store_true", help="Disables automatic Echo Bot for peer-to-peer testing")
    args = parser.parse_args()

    # Mapping of RegionCode to protobuf
    region_val = config_pb2.Config.LoRaConfig.RegionCode.Value(args.region)

    # Initialize Simulator Node
    node = MeshtasticMockNode(
        node_num=DEFAULT_NODE_NUM,
        long_name=DEFAULT_LONG_NAME,
        short_name=DEFAULT_SHORT_NAME,
        region=region_val,
        channel_name=DEFAULT_CHANNEL_NAME,
        enable_echo=not args.no_echo
    )

    log("="*60)
    log("INITIALIZING MESHTASTIC MOCK NODE SIMULATOR")
    log(f"Long Name:   {node.long_name}")
    log(f"Short Name:  {node.short_name}")
    log(f"Node ID (Hex): !{node.node_num:08x} ({node.node_num})")
    log(f"LoRa Region:  {args.region} ({region_val})")
    log(f"Channel 0:    \"{node.channel_name}\" (ModemPreset: LONG_FAST)")
    log("="*60)

    # 1. Start TCP Server
    async def tcp_server_callback(reader, writer):
        handler = TCPConnectionHandler(node, reader, writer)
        await handler.run()

    tcp_server = await asyncio.start_server(tcp_server_callback, '0.0.0.0', args.port)
    log(f"[TCP] Server waiting for connections on port {args.port}...")

    # 2. Start BLE Server (if enabled)
    ble_manager = None
    if not args.no_ble and BLE_AVAILABLE:
        try:
            ble_manager = BLEServerManager(node, args.service_uuid)
            await ble_manager.start()
        except Exception as e:
            log(f"[BLE WARNING] Error starting BLE server: {e}")
            log("[BLE WARNING] Proceed using TCP connections.")
    elif not BLE_AVAILABLE and not args.no_ble:
        log("[BLE WARNING] 'bless' library unavailable in environment. BLE disabled.")

    # 3. Keep application running
    try:
        async with tcp_server:
            while True:
                await asyncio.sleep(3600)
    except KeyboardInterrupt:
        log("Stopping simulator...")
    finally:
        if ble_manager and ble_manager.server:
            log("[BLE] Shutting down GATT Server...")
            await ble_manager.server.stop()
        log("Simulator stopped.")

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        sys.exit(0)
