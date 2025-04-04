FROM rust:1.78-slim as builder

# Create a new empty shell project
RUN USER=root cargo new --bin onyxchat-server
WORKDIR /onyxchat-server

# Install system dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    pkg-config \
    libssl-dev \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Copy over your manifests
COPY ./Cargo.toml ./Cargo.toml
COPY ./Cargo.lock ./Cargo.lock

# This step will cache your dependencies
RUN cargo build --release
RUN rm src/*.rs

# Copy your source code
COPY ./src ./src
COPY ./migrations ./migrations

# Build for release
RUN rm ./target/release/deps/onyxchat_server*
RUN cargo build --release

# Create slim runtime image
FROM debian:bullseye-slim

# Install runtime dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    libssl1.1 \
    libpq5 \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Copy the built binary from the builder stage
COPY --from=builder /onyxchat-server/target/release/onyxchat-server /usr/local/bin/

# Copy migrations
COPY --from=builder /onyxchat-server/migrations /usr/local/bin/migrations

# Create a non-root user
RUN groupadd -r onyxchat && useradd -r -g onyxchat onyxchat
USER onyxchat

# Set working directory
WORKDIR /usr/local/bin

# Set environment variable to locate migrations
ENV RUST_LOG=info

# Run the binary
CMD ["onyxchat-server"] 