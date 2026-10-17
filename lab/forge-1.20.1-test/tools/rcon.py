import socket, struct, sys, time

HOST = "127.0.0.1"
PORT = 25575
PASSWORD = "mcdev"

def pack(req_id, typ, body):
    payload = body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<iii", len(payload) + 8, req_id, typ) + payload

def recv(sock):
    raw = sock.recv(4)
    if len(raw) < 4:
        return None
    (length,) = struct.unpack("<i", raw)
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            break
        data += chunk
    if len(data) < 8:
        return None
    req_id, typ = struct.unpack("<ii", data[:8])
    body = data[8:-2].decode("utf-8", "replace")
    return req_id, typ, body

def main():
    s = socket.create_connection((HOST, PORT), timeout=10)
    s.sendall(pack(1, 3, PASSWORD))
    resp = recv(s)
    print("AUTH", resp)
    if not resp or resp[0] == -1:
        sys.exit(2)
    for cmd in sys.argv[1:]:
        s.sendall(pack(2, 2, cmd))
        print("CMD", cmd, "->", recv(s))
        time.sleep(0.2)
    s.close()

if __name__ == "__main__":
    main()
