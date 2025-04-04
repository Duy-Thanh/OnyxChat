FROM nginx:mainline-alpine

# Copy configuration file
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Create necessary directories
RUN mkdir -p /etc/nginx/ssl /var/www/static /var/www/error_pages

# Copy SSL certificates
COPY certs/server.crt /etc/nginx/ssl/
COPY certs/server.key /etc/nginx/ssl/

# Copy error pages
COPY error_pages/404.html /var/www/error_pages/
COPY error_pages/50x.html /var/www/error_pages/

# Expose ports
EXPOSE 80 443

# Start nginx
CMD ["nginx", "-g", "daemon off;"] 