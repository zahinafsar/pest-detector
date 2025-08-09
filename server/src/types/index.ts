import { Request } from 'express';

// Extend Request interface to include user property
export interface AuthenticatedRequest extends Request {
    user?: any;
    file?: any;
}

// User interface for database operations
export interface User {
    id: string;
    username: string;
    pin: string;
}

// JWT payload interface
export interface JWTPayload {
    userId: string;
    username: string;
}

// Login request interface
export interface LoginRequest {
    username: string;
    pin: string;
}

// Upload response interface
export interface UploadResponse {
    filename: string;
    originalName: string;
    size: number;
    mimetype: string;
    path: string;
} 