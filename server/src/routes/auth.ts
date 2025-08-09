import { Router, Request, Response } from 'express';
import { authenticateToken } from '../middleware/auth';
import { AuthenticatedRequest } from '../types';
import { config } from '../config';
import { prisma } from '../lib/prisma';
import jwt from 'jsonwebtoken';

const router = Router();

// Login route
router.post('/login', async (req: Request, res: Response) => {
    try {
        const { username, pin } = req.body;

        if (!username || !pin) {
            return res.status(400).json({
                error: 'Missing credentials',
                message: 'Username and PIN are required'
            });
        }

        // First check if username exists
        let user = await prisma.user.findFirst({
            where: {
                username: username
            }
        });

        // If username doesn't exist, create new account
        if (!user) {
            try {
                user = await prisma.user.create({
                    data: {
                        username: username,
                        pin: pin
                    }
                });

                console.log('New user created:', user.username);
            } catch (createError: any) {
                console.error('Error creating new user:', createError);
                return res.status(500).json({
                    error: 'Account creation failed',
                    message: 'Failed to create new account. Please try again.'
                });
            }
        } else {
            // Username exists, check if PIN matches
            if (user.pin !== pin) {
                return res.status(401).json({
                    error: 'Invalid credentials',
                    message: 'PIN is incorrect for this username'
                });
            }
        }

        // Generate JWT token
        const token = jwt.sign(
            {
                userId: user.id,
                username: user.username
            },
            config.jwtSecret,
            { expiresIn: config.jwtExpiresIn }
        );

        res.json({
            message: 'Login successful',
            token: token,
            user: {
                id: user.id,
                username: user.username
            }
        });

    } catch (error: any) {
        console.error('Login error:', error);
        res.status(500).json({
            error: 'Internal server error',
            message: error.message
        });
    }
});

// Protected route example
router.get('/profile', authenticateToken, (req: AuthenticatedRequest, res: Response) => {
    res.json({
        message: 'Profile accessed successfully',
        user: req.user
    });
});

export default router; 