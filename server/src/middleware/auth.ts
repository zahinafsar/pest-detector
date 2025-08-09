import { Response, NextFunction } from 'express';
const jwt = require('jsonwebtoken');
import { AuthenticatedRequest } from '../types';
import { config } from '../config';

// Middleware to verify JWT token
export const authenticateToken = (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1]; // Bearer TOKEN

    if (!token) {
        return res.status(401).json({
            error: 'Access denied',
            message: 'No token provided'
        });
    }

    jwt.verify(token, config.jwtSecret, (err: any, user: any) => {
        if (err) {
            return res.status(403).json({
                error: 'Invalid token',
                message: 'Token is not valid'
            });
        }
        req.user = user;
        next();
    });
}; 