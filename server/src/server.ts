import app from './app';
import { config } from './config';

const port = config.port;

app.listen(port, () => {
    console.log(`Server listening on port http://localhost:${port}`);
}); 