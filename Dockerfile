FROM golang:1.26-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .

ARG VERSION=0.3.1
RUN CGO_ENABLED=0 go build -ldflags="-s -w -X main.version=${VERSION}" -o preferences ./cmd/preferences

FROM alpine:3.21
RUN adduser -D -u 65534 appuser
USER appuser
COPY --from=builder /app/preferences /preferences
EXPOSE 9001
ENTRYPOINT ["/preferences"]
