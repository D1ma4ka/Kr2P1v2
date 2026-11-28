"""Send a fixture location through a local Android emulator's GNSS console."""
from pathlib import Path
import re
import socket
import sys


def submit(serial: str, longitude: float, latitude: float) -> None:
    match = re.fullmatch(r"emulator-(\d+)", serial)
    if match is None or not (0 < int(match[1]) <= 65535):
        raise ValueError("Select a local AVD with ANDROID_SERIAL=emulator-PORT")
    if not (-180 <= longitude <= 180 and -90 <= latitude <= 90):
        raise ValueError("Invalid coordinates")
    with socket.create_connection(("127.0.0.1", int(match[1])), timeout=5) as console:
        with console.makefile(encoding="ascii") as replies:
            def read_reply(operation: str) -> list[str]:
                lines = []
                for raw in replies:
                    line = raw.strip()
                    if line == "OK":
                        return lines
                    if line.startswith("KO"):
                        break
                    lines.append(line)
                raise RuntimeError(f"Emulator console did not acknowledge {operation}")

            greeting = read_reply("connection")
            if any("Authentication required" in line for line in greeting):
                token = (Path.home() / ".emulator_console_auth_token").read_text().strip()
                console.sendall(f"auth {token}\n".encode("ascii"))
                read_reply("authentication")
            console.sendall(f"geo fix {longitude} {latitude} 0 8 0\n".encode("ascii"))
            read_reply("GPS input")


if __name__ == "__main__":
    try:
        submit(sys.argv[1], float(sys.argv[2]), float(sys.argv[3]))
    except (OSError, RuntimeError, ValueError) as error:
        sys.exit(f"GPS input failed: {error}")
