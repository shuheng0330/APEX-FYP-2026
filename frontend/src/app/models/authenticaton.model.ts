export interface LoginResponse {
    message: string;
    userId: string;
    accessToken: string;
    roles?: string[];
}