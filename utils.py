import functools
import logging
import time

def cache_result(func):
    """
    Example decorator to cache results of expensive function calls.
    This is just a placeholder. In real usage, you'd store results in a dictionary
    or use 'lru_cache' from functools, etc.
    """
    cache = {}
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        key = (func.__name__, args, frozenset(kwargs.items()))
        if key in cache:
            logging.info(f"Cache hit for {func.__name__}")
            return cache[key]
        result = func(*args, **kwargs)
        cache[key] = result
        return result
    return wrapper

def log_action(action: str):
    """
    Placeholder function for advanced logging.
    Could integrate with a database, external logging service, etc.
    """
    logging.info(f"[ACTION] {action}")

def retry_on_exception(retries=3, delay=1):
    """
    Example decorator to retry a function if it raises an exception.
    """
    def decorator(func):
        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            for attempt in range(retries):
                try:
                    return func(*args, **kwargs)
                except Exception as e:
                    logging.warning(f"Exception on attempt {attempt+1}/{retries}: {e}")
                    time.sleep(delay)
            raise
        return wrapper
    return decorator
