import express from 'express';
import { PrismaClient } from '@prisma/client';

const router = express.Router();
const prisma = new PrismaClient();

// Save detection report
router.post('/report', async (req, res) => {
    try {
        const { userId, insectName, insectType, confidence, imagePath, location, notes } = req.body;

        if (!userId || !insectName || !insectType || confidence === undefined) {
            return res.status(400).json({ 
                success: false, 
                message: 'Missing required fields: userId, insectName, insectType, confidence' 
            });
        }

        // Check if user exists, if not create a temporary user
        let user = await prisma.user.findUnique({
            where: { id: userId }
        });

        if (!user) {
            // Create a temporary user for the report
            user = await prisma.user.create({
                data: {
                    id: userId,
                    username: `User_${userId.substring(0, 8)}`,
                    pin: '0000'
                }
            });
        }

        const report = await prisma.detectionReport.create({
            data: {
                userId,
                insectName,
                insectType,
                confidence: parseFloat(confidence),
                imagePath: imagePath || null,
                location: location || null,
                notes: notes || null,
            },
        });

        res.json({ success: true, report });
    } catch (error) {
        console.error('Error saving detection report:', error);
        res.status(500).json({ 
            success: false, 
            message: 'Failed to save detection report' 
        });
    }
});

// Get detection statistics for a user
router.get('/stats/:userId', async (req, res) => {
    try {
        const { userId } = req.params;

        if (!userId) {
            return res.status(400).json({ 
                success: false, 
                message: 'User ID is required' 
            });
        }

        // Get total detections
        const totalDetections = await prisma.detectionReport.count({
            where: { userId }
        });

        // Get detections by insect type
        const detectionsByType = await prisma.detectionReport.groupBy({
            by: ['insectType'],
            where: { userId },
            _count: { insectType: true },
            orderBy: { _count: { insectType: 'desc' } }
        });

        // Get detections by insect name
        const detectionsByName = await prisma.detectionReport.groupBy({
            by: ['insectName'],
            where: { userId },
            _count: { insectName: true },
            orderBy: { _count: { insectName: 'desc' } }
        });

        // Get recent detections (last 10)
        const recentDetections = await prisma.detectionReport.findMany({
            where: { userId },
            orderBy: { createdAt: 'desc' },
            take: 10,
            select: {
                id: true,
                insectName: true,
                insectType: true,
                confidence: true,
                createdAt: true,
                location: true
            }
        });

        // Get detections by month (last 12 months) - simplified version
        const monthlyStats = await prisma.detectionReport.groupBy({
            by: ['createdAt'],
            where: { 
                userId,
                createdAt: {
                    gte: new Date(Date.now() - 365 * 24 * 60 * 60 * 1000) // Last 12 months
                }
            },
            _count: { createdAt: true },
            orderBy: { createdAt: 'desc' }
        });

        // Get average confidence
        const avgConfidence = await prisma.detectionReport.aggregate({
            where: { userId },
            _avg: { confidence: true }
        });

        res.json({
            success: true,
            stats: {
                totalDetections,
                detectionsByType: detectionsByType.map(item => ({
                    type: item.insectType,
                    count: item._count.insectType
                })),
                detectionsByName: detectionsByName.map(item => ({
                    name: item.insectName,
                    count: item._count.insectName
                })),
                recentDetections,
                monthlyStats: monthlyStats.map(item => ({
                    month: item.createdAt,
                    count: item._count.createdAt
                })),
                averageConfidence: avgConfidence._avg.confidence || 0
            }
        });
    } catch (error) {
        console.error('Error fetching detection statistics:', error);
        res.status(500).json({ 
            success: false, 
            message: 'Failed to fetch detection statistics' 
        });
    }
});

// Get all detection reports for a user
router.get('/reports/:userId', async (req, res) => {
    try {
        const { userId } = req.params;
        const { page = 1, limit = 20 } = req.query;

        if (!userId) {
            return res.status(400).json({ 
                success: false, 
                message: 'User ID is required' 
            });
        }

        const skip = (Number(page) - 1) * Number(limit);

        const [reports, total] = await Promise.all([
            prisma.detectionReport.findMany({
                where: { userId },
                orderBy: { createdAt: 'desc' },
                skip,
                take: Number(limit),
                select: {
                    id: true,
                    insectName: true,
                    insectType: true,
                    confidence: true,
                    imagePath: true,
                    location: true,
                    notes: true,
                    createdAt: true
                }
            }),
            prisma.detectionReport.count({
                where: { userId }
            })
        ]);

        res.json({
            success: true,
            reports,
            pagination: {
                page: Number(page),
                limit: Number(limit),
                total,
                pages: Math.ceil(total / Number(limit))
            }
        });
    } catch (error) {
        console.error('Error fetching detection reports:', error);
        res.status(500).json({ 
            success: false, 
            message: 'Failed to fetch detection reports' 
        });
    }
});

export default router;
