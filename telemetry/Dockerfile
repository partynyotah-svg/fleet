FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY telemetry/requirements.txt .
RUN pip install --no-cache-dir --requirement requirements.txt
COPY telemetry /app/telemetry
RUN useradd --system --uid 10002 --create-home relay
USER 10002:10002
EXPOSE 8090
HEALTHCHECK --interval=15s --timeout=3s --retries=5 CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8090/healthz', timeout=2)"
CMD ["uvicorn", "telemetry.ingest:app", "--host", "0.0.0.0", "--port", "8090", "--proxy-headers", "--forwarded-allow-ips", "*"]