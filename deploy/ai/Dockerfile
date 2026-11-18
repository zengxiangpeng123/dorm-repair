FROM python:3.12-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
COPY app/ai/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app/ai/ .
RUN useradd --system --uid 10001 aiuser && chown -R aiuser:aiuser /app
USER aiuser
EXPOSE 8001
CMD ["uvicorn","app:app","--host","0.0.0.0","--port","8001"]
