// Configuration settings
export const config = {
    port: process.env.PORT || 8001,
    jwtSecret: process.env.JWT_SECRET || 'your-secret-key',
    jwtExpiresIn: 24 * 60 * 60 * 1000, // 24 hours
    upload: {
        maxFileSize: 10 * 1024 * 1024, // 10MB
        allowedMimeTypes: ['image/jpeg', 'image/png', 'image/gif', 'image/webp'],
        tempDir: 'temp'
    }
}; 