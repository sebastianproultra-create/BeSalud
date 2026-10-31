FROM node:20-slim

# Chromium del sistema para whatsapp-web.js (puppeteer no descarga el suyo)
RUN apt-get update && apt-get install -y --no-install-recommends chromium fonts-noto-color-emoji git ca-certificates \
    && rm -rf /var/lib/apt/lists/*
ENV PUPPETEER_SKIP_DOWNLOAD=true \
    PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium \
    DATA_DIR=/data

WORKDIR /app
COPY package*.json ./
# whatsapp-web.js viene del repositorio oficial y el lockfile lo pide por SSH: en el contenedor no hay llaves
RUN git config --global url."https://github.com/".insteadOf ssh://git@github.com/ \
    && npm ci --omit=dev
COPY . .
CMD ["node", "index.js"]
