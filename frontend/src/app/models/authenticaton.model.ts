export interface LoginResponse {
    message: string;
    userId: string;
    accessToken: string;
    roles?: string[];
}

export interface CurrentUserResponse {
    userId: string;
    roles?: string[];
}
