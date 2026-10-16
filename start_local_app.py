import os
import shutil
import signal
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
BACKEND_DIR = ROOT / "backend"
WEB_DIR = ROOT / "web"
TELEMETRY_DIR = ROOT / "telemetry"
VENV_PYTHON = ROOT / ".venv" / "Scripts" / "python.exe"
MAVEN_FALLBACK = Path(r"C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd")


def load_env_file(path: Path):
    values = {}
    if not path.exists():
        return values

    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


ENV_VALUES = load_env_file(ROOT / ".env")


def ensure_env(key: str, default: str = "") -> str:
    return os.environ.get(key) or ENV_VALUES.get(key, default)


def find_python_executable() -> str:
    if VENV_PYTHON.exists():
        return str(VENV_PYTHON)
    return sys.executable


def find_maven_executable() -> str | None:
    return shutil.which("mvn") or (str(MAVEN_FALLBACK) if MAVEN_FALLBACK.exists() else None)


def wait_for_backend(timeout: int = 90) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen("http://localhost:8080/actuator/health", timeout=2) as response:
                if response.status == 200:
                    return True
        except (urllib.error.URLError, TimeoutError):
            time.sleep(2)
    return False


def run_command(name: str, command: str, cwd: str | None = None, env: dict | None = None, shell: bool = True):
    print(f"\n[{name}] Starting: {command}")
    process = subprocess.Popen(
        command,
        cwd=cwd,
        env=env,
        shell=shell,
    )
    return process


def check_command_exists(command_name: str) -> bool:
    return shutil.which(command_name) is not None


def ensure_postgres_container():
    if not check_command_exists("docker"):
        print("[Postgres] Docker is not installed or not on PATH. Skipping PostgreSQL launch.")
        return

    db_name = ensure_env("POSTGRES_DB", "relayfleet")
    db_user = ensure_env("POSTGRES_USER", "relayfleet")
    db_pass = ensure_env("POSTGRES_PASSWORD", "StrongLocalPassword123!")

    result = subprocess.run(
        ["docker", "ps", "--filter", "name=relay-postgres", "--format", "{{.Names}}"],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=False,
    )
    if result.stdout.strip() == "relay-postgres":
        print("[Postgres] Container relay-postgres is already running.")
        return

    print("[Postgres] Starting container relay-postgres...")
    subprocess.run(
        [
            "docker",
            "run",
            "--name",
            "relay-postgres",
            "-e",
            f"POSTGRES_DB={db_name}",
            "-e",
            f"POSTGRES_USER={db_user}",
            "-e",
            f"POSTGRES_PASSWORD={db_pass}",
            "-p",
            "5432:5432",
            "-d",
            "postgres:17-alpine",
        ],
        check=False,
    )


def build_backend_env() -> dict:
    env = os.environ.copy()
    env["DATABASE_URL"] = ensure_env("DATABASE_URL", "jdbc:h2:file:./relayfleet-local;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE")
    env["DATABASE_USER"] = ensure_env("DATABASE_USER", "sa")
    env["DATABASE_PASSWORD"] = ensure_env("DATABASE_PASSWORD", "")
    env["SESSION_COOKIE_SECURE"] = ensure_env("SESSION_COOKIE_SECURE", "false")
    env["APP_ORIGIN"] = ensure_env("APP_ORIGIN", "http://localhost:8080")
    env["INGEST_SERVICE_TOKEN"] = ensure_env("INGEST_SERVICE_TOKEN", "local-dev-token")
    env["BOOTSTRAP_ADMIN_EMAIL"] = ensure_env("BOOTSTRAP_ADMIN_EMAIL", "lewis.nyotah@gmail.com")
    env["BOOTSTRAP_ADMIN_PASSWORD"] = ensure_env("BOOTSTRAP_ADMIN_PASSWORD", "Lewis@2025#")
    env["GOOGLE_MAPS_API_KEY"] = ensure_env("GOOGLE_MAPS_API_KEY", "")
    env["VEHICLE_COMMAND_ADAPTER"] = ensure_env("VEHICLE_COMMAND_ADAPTER", "disabled")
    env["SERVER_PORT"] = ensure_env("SERVER_PORT", "8080")
    env["PORT"] = ensure_env("PORT", "8080")
    return env


def build_ingest_env() -> dict:
    env = os.environ.copy()
    env["FLEET_INTERNAL_URL"] = ensure_env("FLEET_INTERNAL_URL", "http://127.0.0.1:8080/api/v1/internal/ingest")
    env["INGEST_SERVICE_TOKEN"] = ensure_env("INGEST_SERVICE_TOKEN", "local-dev-token")
    env["MAX_BODY_BYTES"] = ensure_env("MAX_BODY_BYTES", "16384")
    return env


def main():
    print("Relay Fleet local launcher")
    print(f"Project root: {ROOT}")
    print("This script will start PostgreSQL, the Java backend, and the ingest service. The web app is served from the backend on the same origin to avoid cross-origin login failures.")

    if not (ROOT / ".env").exists():
        print("\nMissing .env file. Copy .env.example to .env and update the values first.")
        input("Press Enter to exit...")
        return

    ensure_postgres_container()

    backend_env = build_backend_env()
    ingest_env = build_ingest_env()
    venv_python = find_python_executable()
    maven = find_maven_executable()
    if not maven:
        print("Maven was not found. Install Maven or add mvn to PATH before starting the backend.")
        return

    backend_proc = run_command(
        "backend",
        f'"{maven}" spring-boot:run -Dspring-boot.run.arguments=--server.port=8080',
        str(BACKEND_DIR),
        env=backend_env,
    )

    print("Waiting for backend health check...")
    if not wait_for_backend():
        print("Backend did not become healthy at http://localhost:8080. Check the backend console for startup errors.")
        backend_proc.terminate()
        return

    ingest_proc = run_command(
        "ingest",
        f'"{venv_python}" -m uvicorn ingest:app --host 0.0.0.0 --port 8000',
        str(TELEMETRY_DIR),
        env=ingest_env,
    )

    print("\nServices started.")
    print("- Backend: http://localhost:8080")
    print("- Ingest: http://localhost:8000")
    print("- Web UI: http://localhost:8080")
    print("\nPress Ctrl+C to stop all services.")

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nStopping services...")

    for proc in (backend_proc, ingest_proc):
        try:
            if os.name == "nt":
                proc.terminate()
            else:
                proc.send_signal(signal.SIGINT)
        except Exception:
            pass

        try:
            proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            proc.kill()


if __name__ == "__main__":
    main()
