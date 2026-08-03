import subprocess
import time
import sys


def before_all(context):
    context.server_process = subprocess.Popen(
        [sys.executable, "-u", "mock_server.py", "--no-ble", "--port", "4403"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    time.sleep(1.5)


def after_all(context):
    if hasattr(context, 'server_process'):
        context.server_process.terminate()
        context.server_process.wait()


def after_scenario(context, scenario):
    if hasattr(context, 'client_a'):
        context.client_a.disconnect()
    if hasattr(context, 'client_b'):
        context.client_b.disconnect()
