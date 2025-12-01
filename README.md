# Java TCP Load Balancer

A multi-threaded Layer 4 load balancer built in Java without external frameworks. I created this project to understand the low-level mechanics of traffic routing, socket management, and concurrency control.

It currently uses a **Least Connections** strategy to distribute traffic between backend servers and includes a self-healing mechanism that removes unresponsive servers from rotation.

## Technical Implementation

* **Core:** Raw `ServerSocket` implementation handling bytes via custom input/output stream piping.
* **Concurrency:** Thread-per-connection model.
* **Algorithm:** Least Connections (directs traffic to the server with the lowest active load).
* **Health Checks:** Background daemon pings backend servers every 3 seconds to verify uptime.
* **Monitoring:** Console-based telemetry reporting active connections and server status.

## Project Structure

```text
src/
├── LoadBalancer.java    # Main entry point, handles connection acceptance
├── BackendRegistry.java # Thread-safe state management for backend servers
├── StreamPipe.java      # Bi-directional byte shuffling
└── Config.java          # Static configuration loader
```

## How to Run

### 1. Configure Backend
Update `Config.properties` to point to your backend servers (e.g., Python `http.server`, Node, or Tomcat).

```properties
listen.port=8080
backend.servers=9001,9002
health.check.interval=3000
```

### 2. Build and Start
You can run this directly from an IDE (IntelliJ/Eclipse) or compile via command line:

```bash
javac src/*.java -d out
java -cp out LoadBalancer
```

## Performance Testing

I used **Apache Bench (ab)** to validate concurrency and stability under load.

**Test Command:**
```bash
# 1000 requests, 10 concurrent connections
ab -n 1000 -c 10 http://localhost:8080/
```

*Note: Since this implementation uses a thread-per-request model, high concurrency (>1000) may be limited by OS thread limits. Future improvements would involve refactoring to Java NIO (Selector) or an ExecutorService.*
