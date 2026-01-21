

# 🚀 Distributed TF-IDF Search Engine

> **Course:** Distributed Systems Fundamentals
> **Status:** Complete (Stage 1 & Stage 2)

This project is a fully functional **Distributed Text Search Engine** built in **Java**. It allows users to search for terms across a set of documents using the **TF-IDF** (Term Frequency - Inverse Document Frequency) algorithm.

The system is designed to be **fault-tolerant**, **scalable**, and **decoupled**, featuring a dedicated Frontend Gateway that manages user requests and a backend Zookeeper-coordinated cluster that performs the parallel computation.

---

## 🏗️ System Architecture

The project follows a robust **3-Tier Architecture**:

### 1. 🌐 Frontend Gateway (Stage 2)

* **Role:** The entry point for all clients.
* **Responsibility:**
* Host the **Web UI** (HTML/JS) for users.
* Perform **Service Discovery** by querying Zookeeper to find the current active Leader.
* Forward search requests to the Leader via **HTTP**.
* Serve document content directly to the user when a result is clicked.


* **Benefit:** Decouples the client from the backend. If the Leader node dies and a new one is elected, the Frontend automatically detects the change and routes traffic to the new Leader.

### 2. 👑 The Coordinator (Leader)

* **Role:** The "Brain" of the search cluster.
* **Responsibility:**
* Receives search tasks from the Frontend.
* Identifies active **Worker** nodes from Zookeeper.
* Distributes the document corpus among available workers(the docs are centralized they are all in one folder but we let each worker access some of them).
* Sends calculation tasks via **gRPC** (High-performance RPC).
* Aggregates results and calculates the global **IDF** score.


* **Leader Election:** If the Leader crashes, Zookeeper automatically elects a new Leader from the available Workers.

### 3. 👷 The Workers

* **Role:** The "Muscle" of the cluster.
* **Responsibility:**
* Receive a list of file paths from the Leader.
* Calculate the **Term Frequency (TF)** for the search query in parallel.
* Return the scores to the Leader.


* **Scalability:** we can add new Worker nodes at runtime, and the system will automatically utilize them for the next search request.

---

## 🛠️ Technologies Used

* **Language:** Java 17+
* **Coordination:** Apache Zookeeper
* **Communication:**
* **External:** HTTP / REST (Frontend  Leader)
* **Internal:** gRPC + Protobuf (Leader  Worker)


* **Algorithm:** TF-IDF (Term Frequency-Inverse Document Frequency)
* **Frontend:** HTML5, CSS3, JavaScript

---

## ✨ Key Features

* **⚡ High Performance:** Uses gRPC for internal node communication, ensuring low latency.
* **🛡️ Fault Tolerance:**
* **Leader Failure:** Automatic re-election; the system self-heals.
* **Worker Failure:** The Leader detects missing workers (via Zookeeper watchers) and re-distributes tasks (future scope).


* **📈 Dynamic Scalability:** Nodes can join or leave the cluster dynamically without restarting the system.
* **🖥️ User Interface:** A clean, responsive Web UI to search and view documents.
* **🔗 Service Discovery:** The Frontend never hardcodes IP addresses; it discovers the Leader dynamically.

---

## 🚀 How to Run the Project

### Prerequisites

* Java JDK 17+
* Apache Maven
* Apache Zookeeper (Running on port `2181`)

### 1. Build the Project

```bash
mvn clean install

```

### 2. Start the Components

**Step A: Start Zookeeper**
Ensure Zookeeper is running on `localhost:2181`.

**Step B: Start the Backend Cluster**
Run multiple instances of `SearchNode` on different ports.

* **Node 1 (Leader):** `java -cp ... com.distributed.search.SearchNode 8081`
* **Node 2 (Worker):** `java -cp ... com.distributed.search.SearchNode 8082`
* **Node 3 (Worker):** `java -cp ... com.distributed.search.SearchNode 8083`

**Step C: Start the Frontend**
Run the Frontend server, which will act as the gateway.

* **Frontend:** `java -cp ... com.distributed.search.frontend.Frontend 9000`

### 3. Access the Application

Open your browser and go to:
👉 **`http://localhost:9000`**

1. Enter a search term (e.g., "distributed").
2. Click **Search**.
3. Click on any file in the results to view its content!

---

### 👨‍💻 Author

**[Hussein]**
