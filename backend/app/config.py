from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    app_name: str = "Appbllocker API"
    secret_key: str = "change-me-in-production"
    database_url: str = "sqlite:///./appblocker.db"
    access_token_expire_minutes: int = 60 * 24

    class Config:
        env_file = ".env"


settings = Settings()
