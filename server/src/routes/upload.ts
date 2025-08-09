import { Router, Response } from 'express';
import { authenticateToken } from '../middleware/auth';
import { upload } from '../middleware/upload';
import { AuthenticatedRequest } from '../types';

const router = Router();

// Upload route
router.post('/upload', authenticateToken, upload.single('image'), (req: AuthenticatedRequest, res: Response) => {
    try {
        if (!req.file) {
            return res.status(400).json({
                error: 'No image file provided',
                message: 'Please upload an image file using the "image" field'
            });
        }

        res.json({
            message: 'Image uploaded successfully',
            file: {
                filename: req.file.filename,
                originalName: req.file.originalname,
                size: req.file.size,
                mimetype: req.file.mimetype,
                path: req.file.path
            }
        });

    } catch (error: any) {
        console.error('Error processing upload:', error);
        res.status(500).json({
            error: 'Internal server error',
            message: error.message
        });
    }
});

export default router; 