FROM node:20-slim

# Chromium del sistema para whatsapp-web.js (puppeteer no descarga el suyo)
RUN apt-get update && apt-get install -y --no-install-recommends chromium fonts-noto-color-emoji \
    && rm -rf /var/lib/apt/lists/*
ENV PUPPETEER_SKIP_DOWNLOAD=true \
    PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium \
    DATA_DIR=/data

WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY . .
CMD ["node", "index.js"]
