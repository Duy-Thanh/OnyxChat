/**
 * File Upload Middleware
 * 
 * Handles file uploads with configurable size limits and file types
 */

const multer = require('multer');
const path = require('path');
const fs = require('fs');
const { v4: uuidv4 } = require('uuid');
const config = require('../config');
const logger = require('../utils/logger');

// Ensure upload directory exists
const uploadDir = path.resolve(process.cwd(), config.fileUpload.storagePath);
if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir, { recursive: true });
  logger.info(`Created upload directory: ${uploadDir}`);
}

// Configure storage
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    // Store files directly in the main uploads directory
    // Remove date-based subdirectories to avoid path mismatches
    cb(null, uploadDir);
  },
  filename: (req, file, cb) => {
    // Generate a unique filename with original extension and metadata
    const fileExtension = path.extname(file.originalname);
    const fileName = `${uuidv4()}${fileExtension}`;
    
    // Store file metadata in request for later use
    req.fileMetadata = {
      originalName: file.originalname,
      fileName: fileName,
      mimeType: file.mimetype,
      size: 0, // Will be updated after upload
      uploadDate: new Date(),
      userId: req.user?.id // Assuming auth middleware sets req.user
    };
    
    cb(null, fileName);
  }
});

// Enhanced file filter with more robust mime type validation
const fileFilter = (req, file, cb) => {
  const allowedTypes = config.fileUpload.allowedTypes.split(',');
  
  // Check if the file type is allowed
  const isAllowed = allowedTypes.some(type => {
    if (type.endsWith('/*')) {
      const category = type.split('/')[0];
      return file.mimetype.startsWith(`${category}/`);
    }
    return file.mimetype === type;
  });

  // Additional security checks
  const hasValidExtension = /\.(jpg|jpeg|png|gif|mp4|mp3|wav|pdf|doc|docx|xls|xlsx|zip)$/i.test(file.originalname);
  
  if (isAllowed && hasValidExtension) {
    cb(null, true);
  } else {
    cb(new Error(`File type not allowed: ${file.mimetype}`), false);
  }
};

// Create multer upload instance
const upload = multer({
  storage,
  fileFilter,
  limits: {
    fileSize: config.fileUpload.maxSize
  }
});

// Error handling middleware
const handleUploadError = (err, req, res, next) => {
  if (err instanceof multer.MulterError) {
    if (err.code === 'LIMIT_FILE_SIZE') {
      return res.status(413).json({ 
        error: 'File too large',
        message: `File size exceeds the limit of ${Math.floor(config.fileUpload.maxSize / (1024 * 1024))}MB`
      });
    }
    return res.status(400).json({ error: 'Upload error', message: err.message });
  }
  
  if (err) {
    logger.error('File upload error:', err);
    return res.status(400).json({ error: 'Upload failed', message: err.message });
  }
  
  next();
};

// Add file cleanup middleware
const cleanupOnError = (err, req, res, next) => {
  if (err && req.file) {
    // Delete the uploaded file if there was an error
    fs.unlink(req.file.path, (unlinkError) => {
      if (unlinkError) {
        logger.error('Error deleting file after upload error:', unlinkError);
      }
    });
  }
  next(err);
};

// Add file size tracking middleware
const trackFileSize = (req, res, next) => {
  if (req.file && req.fileMetadata) {
    req.fileMetadata.size = req.file.size;
    
    // Update file path to be relative to storage root
    req.fileMetadata.path = path.relative(uploadDir, req.file.path);
    
    // Log upload success
    logger.info('File uploaded successfully:', {
      ...req.fileMetadata,
      path: req.file.path
    });
  }
  next();
};

module.exports = {
  upload,
  handleUploadError,
  cleanupOnError,
  trackFileSize
}; 