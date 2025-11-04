-- Add authentication related fields to users table
ALTER TABLE users 
ADD COLUMN refresh_token TEXT,
ADD COLUMN refresh_token_expires_at TIMESTAMP,
ADD COLUMN last_login_at TIMESTAMP;

-- Create index for refresh token lookup
CREATE INDEX idx_users_refresh_token ON users(refresh_token) WHERE refresh_token IS NOT NULL;

-- Create index for active users
CREATE INDEX idx_users_active ON users(is_active) WHERE is_active = true;