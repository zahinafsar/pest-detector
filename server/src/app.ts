import express from 'express';
import cors from 'cors';
import morgan from 'morgan';
import authRoutes from './routes/auth';
import uploadRoutes from './routes/upload';
import { errorHandler } from './middleware/errorHandler';

const app = express();

// Middleware
app.use(morgan('dev'));
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Routes
app.get('/', (req, res) => {
    res.json({message: 'Hello World!'});
});

app.use('/auth', authRoutes);
app.use('/api', uploadRoutes);

// Error handling middleware (must be last)
app.use(errorHandler);

export default app; 