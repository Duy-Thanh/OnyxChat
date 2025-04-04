# OnyxChat Server Makefile

# Variables
SERVER_BIN = target/release/onyxchat-server
SERVER_PORT = 8080
SERVER_HOST = 0.0.0.0

.PHONY: all build run test clean dev check examples

all: build

# Build the server in release mode
build:
	cargo build --release

# Run the server in release mode
run: build
	$(SERVER_BIN)

# Run the server in development mode with auto-reload (requires cargo-watch)
dev:
	cargo watch -x run

# Run the tests
test:
	cargo test

# Run cargo check
check:
	cargo check

# Format the code
fmt:
	cargo fmt

# Run the linter
lint:
	cargo clippy

# Clean build artifacts
clean:
	cargo clean

# Run the test script
test-api: build
	./test_server.sh

# Install dependencies for the examples
setup-examples:
	@echo "Installing dependencies for Python example..."
	pip install requests websocket-client

# Run the examples
run-examples: setup-examples
	@echo "Running JavaScript example (requires Node.js)..."
	node examples/client.js
	@echo "Running Python example..."
	python examples/client.py

# Print help information
help:
	@echo "OnyxChat Server Makefile"
	@echo ""
	@echo "Usage:"
	@echo "  make build      - Build the server in release mode"
	@echo "  make run        - Run the server in release mode"
	@echo "  make dev        - Run the server in development mode with auto-reload"
	@echo "  make test       - Run the tests"
	@echo "  make check      - Run cargo check"
	@echo "  make fmt        - Format the code"
	@echo "  make lint       - Run the linter"
	@echo "  make clean      - Clean build artifacts"
	@echo "  make test-api   - Run the test script"
	@echo "  make examples   - Run the example clients"
	@echo "  make help       - Print this help information" 