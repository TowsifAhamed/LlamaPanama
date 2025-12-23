#ifndef LLAMAPANAMA_H
#define LLAMAPANAMA_H

#ifdef _WIN32
  #ifdef LLAMAPANAMA_EXPORTS
    #define LP_API __declspec(dllexport)
  #else
    #define LP_API __declspec(dllimport)
  #endif
#else
  #define LP_API __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct lp_model lp_model;
typedef struct lp_context lp_context;

typedef struct lp_inference_stats {
    double first_token_ms;
    double tokens_per_sec;
    double total_ms;
    int tokens_emitted;
} lp_inference_stats;

LP_API int lp_backend_init();
LP_API lp_model* lp_model_load(const char* path, int n_gpu_layers, int* err);
LP_API lp_context* lp_context_create(lp_model* model, int ctx, int threads, int* err);
LP_API int lp_tokenize(lp_model* model, const char* text, int add_bos, int* out_tokens, int max_tokens, int* err);
LP_API int lp_eval(lp_context* context, const int* tokens, int n_tokens, int* err);
LP_API int lp_sample(lp_context* context, float temp, float top_p, int top_k, float repeat_penalty, int seed, int* err);
LP_API int lp_sample_ex(lp_context* context, float temp, float top_p, int top_k, float repeat_penalty, int seed, const char* grammar, int* state_pos, int* err);
LP_API int lp_token_to_piece(lp_model* model, int token, char* out, int out_len, int* err);
LP_API int lp_embeddings_dim(lp_model* model, int* err);
LP_API int lp_get_embeddings(lp_context* context, const char* text, float* out, int max_len, int* err);
LP_API void lp_free_model(lp_model* model);
LP_API void lp_free_context(lp_context* context);
LP_API const char* lp_last_error();
LP_API int lp_get_last_stats(lp_context* context, lp_inference_stats* out, int* err);

#ifdef __cplusplus
}
#endif

#endif
