import { Request, Response, NextFunction } from 'express';
import { MulterError } from 'multer';

// Error handling middleware for multer errors
export const errorHandler = (error: Error, req: Request, res: Response, next: NextFunction) => {
    if (error instanceof MulterError) {
        if (error.code === 'LIMIT_FILE_SIZE') {
            return res.status(400).json({
                error: 'File too large',
                message: 'File size must be less than 10MB'
            });
        }
        return res.status(400).json({
            error: 'Upload error',
            message: error.message
        });
    }

    if (error.message === 'Only image files are allowed!') {
        return res.status(400).json({
            error: 'Invalid file type',
            message: error.message
        });
    }

    // Default error handler
    console.error('Unhandled error:', error);
    res.status(500).json({
        error: 'Internal server error',
        message: 'Something went wrong'
    });
}; 