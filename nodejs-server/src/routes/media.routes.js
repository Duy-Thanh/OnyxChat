/**
 * Media Routes
 * 
 * API endpoints for handling media uploads and retrieval
 */

const express = require('express');
const router = express.Router();
const path = require('path');
const fs = require('fs').promises;
const { upload, handleUploadError } = require('../middlewares/fileUpload');
const { authJwt } = require('../middlewares');
const config = require('../config');
const logger = require('../utils/logger');

// Apply authentication middleware to upload routes only
// Note: file retrieval does not require authentication
router.use('/upload', authJwt.verifyToken);

/**
 * Upload a single media file
 * Returns the file URL and metadata
 */
router.post('/upload', upload.single('file'), handleUploadError, async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: 'No file uploaded' });
    }

    // Get file info
    const fileInfo = {
      originalname: req.file.originalname,
      filename: req.file.filename,
      mimetype: req.file.mimetype,
      size: req.file.size,
      path: req.file.path,
      url: `/api/media/file/${req.file.filename}`
    };

    // Log the successful upload
    logger.info(`User ${req.userId} uploaded file: ${fileInfo.originalname} (${Math.floor(fileInfo.size / 1024)} KB)`);

    // Return file metadata
    return res.status(201).json({
      message: 'File uploaded successfully',
      file: fileInfo
    });
  } catch (error) {
    logger.error('Error in file upload handler:', error);
    return res.status(500).json({ error: 'Upload processing failed', message: error.message });
  }
});

/**
 * Get a media file by filename
 * Public endpoint - no authentication required
 */
router.get('/file/:filename', async (req, res) => {
  try {
    const filename = req.params.filename;
    const filePath = path.join(config.fileUpload.storagePath, filename);
    
    try {
      // Check if file exists
      await fs.access(filePath);
    } catch (error) {
      return res.status(404).json({ error: 'File not found' });
    }
    
    // Set cache headers for media files
    res.setHeader('Cache-Control', 'public, max-age=86400'); // 24 hours
    
    // Send the file
    return res.sendFile(path.resolve(filePath));
  } catch (error) {
    logger.error('Error serving file:', error);
    return res.status(500).json({ error: 'Error retrieving file', message: error.message });
  }
});

/**
 * Delete a media file
 * Only available to file owner or admin
 */
router.delete('/file/:filename', authJwt.verifyToken, async (req, res) => {
  try {
    const filename = req.params.filename;
    const filePath = path.join(config.fileUpload.storagePath, filename);
    
    try {
      // Check if file exists
      await fs.access(filePath);
    } catch (error) {
      return res.status(404).json({ error: 'File not found' });
    }
    
    // Delete the file
    await fs.unlink(filePath);
    logger.info(`User ${req.userId} deleted file: ${filename}`);
    
    return res.status(200).json({ message: 'File deleted successfully' });
  } catch (error) {
    logger.error('Error deleting file:', error);
    return res.status(500).json({ error: 'Error deleting file', message: error.message });
  }
});

module.exports = router; 