/**
 * Email Service
 * Handles sending emails for OnyxChat application
 */
const nodemailer = require('nodemailer');
const logger = require('../utils/logger');

// Initialize transporter with environment variables
let transporter = null;

/**
 * Initialize the email transporter
 * Should be called when the server starts
 */
const initializeTransporter = () => {
  try {
    // Only initialize if not already initialized
    if (transporter) return;

    // Create transporter based on environment variables
    transporter = nodemailer.createTransport({
      service: process.env.EMAIL_SERVICE === 'smtp' ? null : process.env.EMAIL_SERVICE,
      host: process.env.EMAIL_HOST,
      port: parseInt(process.env.EMAIL_PORT || '587', 10),
      secure: process.env.EMAIL_SECURE === 'true',
      auth: {
        user: process.env.EMAIL_USER,
        pass: process.env.EMAIL_PASSWORD
      }
    });

    logger.info('Email service initialized successfully');
  } catch (error) {
    logger.error(`Failed to initialize email service: ${error.message}`);
    throw error;
  }
};

/**
 * Send an email
 * @param {Object} options - Email options
 * @param {string} options.to - Recipient email
 * @param {string} options.subject - Email subject
 * @param {string} options.text - Plain text content
 * @param {string} options.html - HTML content (optional)
 * @returns {Promise<boolean>} - Success status
 */
const sendEmail = async (options) => {
  try {
    // Ensure transporter is initialized
    if (!transporter) {
      initializeTransporter();
    }

    // If still not available, log error and return
    if (!transporter) {
      logger.error('Email service not initialized');
      return false;
    }

    // Default sender from environment variable
    const from = process.env.EMAIL_FROM || 'OnyxChat <noreply@onyxchat.com>';

    // Send email
    const info = await transporter.sendMail({
      from,
      to: options.to,
      subject: options.subject,
      text: options.text,
      html: options.html
    });

    logger.info(`Email sent: ${info.messageId}`);
    return true;
  } catch (error) {
    logger.error(`Failed to send email: ${error.message}`);
    return false;
  }
};

/**
 * Send OTP verification email for password reset
 * @param {string} email - Recipient email
 * @param {string} otp - One-time password
 * @returns {Promise<boolean>} - Success status
 */
const sendOtpEmail = async (email, otp) => {
  const subject = 'OnyxChat Password Reset Code';
  const text = `Your OnyxChat password reset code is: ${otp}\n\nThis code will expire in 15 minutes.\n\nIf you did not request a password reset, please ignore this email.`;
  const html = `
    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 5px;">
      <h2 style="color: #333; text-align: center;">OnyxChat Password Reset</h2>
      <p>You requested a password reset for your OnyxChat account.</p>
      <div style="background-color: #f5f5f5; padding: 15px; border-radius: 5px; text-align: center; margin: 20px 0;">
        <h3 style="margin: 0; font-size: 24px; letter-spacing: 5px;">${otp}</h3>
      </div>
      <p>This code will expire in <strong>15 minutes</strong>.</p>
      <p>If you did not request a password reset, please ignore this email.</p>
      <div style="margin-top: 30px; padding-top: 20px; border-top: 1px solid #e0e0e0; text-align: center; color: #777; font-size: 12px;">
        <p>This is an automated message from OnyxChat. Please do not reply to this email.</p>
      </div>
    </div>
  `;

  return sendEmail({ to: email, subject, text, html });
};

module.exports = {
  initializeTransporter,
  sendEmail,
  sendOtpEmail
};
