# Insect Detector Server

A modular Express.js server for the Insect Detector application with authentication and file upload capabilities.

## Project Structure

```
server/
├── src/
│   ├── config/          # Configuration settings
│   │   └── index.ts
│   ├── middleware/      # Express middleware
│   │   ├── auth.ts      # JWT authentication
│   │   ├── upload.ts    # File upload configuration
│   │   └── errorHandler.ts # Error handling
│   ├── routes/          # API routes
│   │   ├── auth.ts      # Authentication routes
│   │   └── upload.ts    # File upload routes
│   ├── types/           # TypeScript type definitions
│   │   └── index.ts
│   ├── app.ts           # Express app setup
│   └── server.ts        # Server entry point
├── package.json
├── tsconfig.json
└── README.md
```

## Features

- **Authentication**: JWT-based authentication with login endpoint
- **File Upload**: Secure image upload with validation and size limits
- **Error Handling**: Comprehensive error handling for various scenarios
- **TypeScript**: Full TypeScript support with proper type definitions
- **Modular Design**: Clean separation of concerns with modular architecture

## API Endpoints

### Authentication
- `POST /auth/login` - User login with username and PIN
- `GET /auth/profile` - Get user profile (protected)

### File Upload
- `POST /api/upload` - Upload image file (protected)

## Getting Started

### Installation
```bash
npm install
```

### Development
```bash
npm run dev
```

### Production
```bash
npm run build
npm run serve
```

### Concurrent Development (with Python server)
```bash
npm start
```

## Configuration

Configuration is centralized in `src/config/index.ts`:

- `port`: Server port (default: 3000)
- `jwtSecret`: JWT secret key
- `jwtExpiresIn`: Token expiration time
- `upload.maxFileSize`: Maximum file size (10MB)
- `upload.allowedMimeTypes`: Allowed image types
- `upload.tempDir`: Temporary directory for uploads

## Environment Variables

- `PORT`: Server port
- `JWT_SECRET`: JWT secret key (required in production)

## Database

This server uses Prisma with the following models:
- `User`: User authentication data

## Security Features

- JWT token authentication
- File type validation (images only)
- File size limits
- Secure file naming with timestamps
- Error handling for invalid uploads 