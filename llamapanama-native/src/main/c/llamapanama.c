#include "llamapanama.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <time.h>

struct lp_model {
    char *path;
};

struct lp_context {
    lp_model *model;
    int ctx;
    int threads;
    int step;
    int seed;
    int sampler_state;
    double eval_start_ms;
    double first_token_ms;
    int tokens_emitted;
};

static _Thread_local char last_error[256];

static void set_error(const char *message) {
    if (message) {
        snprintf(last_error, sizeof(last_error), "%s", message);
    } else {
        last_error[0] = '\0';
    }
}

const char* lp_last_error() {
    return last_error;
}

int lp_backend_init() {
    set_error(NULL);
    return 0;
}

static double now_ms() {
#ifdef _WIN32
    struct timespec ts;
    timespec_get(&ts, TIME_UTC);
#else
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
#endif
    return (double)ts.tv_sec * 1000.0 + (double)ts.tv_nsec / 1.0e6;
}

lp_model* lp_model_load(const char* path, int n_gpu_layers, int* err) {
    (void)n_gpu_layers;
    set_error(NULL);
    if (err) *err = 0;
    lp_model *model = (lp_model*)calloc(1, sizeof(lp_model));
    if (!model) {
        if (err) *err = 1;
        set_error("Out of memory");
        return NULL;
    }
    if (path) {
        size_t len = strlen(path);
        model->path = (char*)malloc(len + 1);
        if (!model->path) {
            free(model);
            if (err) *err = 1;
            set_error("Out of memory");
            return NULL;
        }
        memcpy(model->path, path, len + 1);
    }
    return model;
}

lp_context* lp_context_create(lp_model* model, int ctx, int threads, int* err) {
    set_error(NULL);
    if (err) *err = 0;
    if (!model) {
        if (err) *err = 1;
        set_error("Model is null");
        return NULL;
    }
    lp_context *context = (lp_context*)calloc(1, sizeof(lp_context));
    if (!context) {
        if (err) *err = 1;
        set_error("Out of memory");
        return NULL;
    }
    context->model = model;
    context->ctx = ctx;
    context->threads = threads;
    context->step = 0;
    context->seed = 0;
    context->sampler_state = 0;
    context->eval_start_ms = 0.0;
    context->first_token_ms = 0.0;
    context->tokens_emitted = 0;
    return context;
}

static int fake_vocab_token(const char* text) {
    if (!text) return 0;
    if (strncmp(text, "Hello", 5) == 0) {
        return 1;
    }
    if (strncmp(text, "world", 5) == 0) {
        return 2;
    }
    return 4;
}

int lp_tokenize(lp_model* model, const char* text, int add_bos, int* out_tokens, int max_tokens, int* err) {
    (void)model;
    set_error(NULL);
    if (err) *err = 0;
    if (!text || !out_tokens || max_tokens <= 0) {
        if (err) *err = 1;
        set_error("Invalid arguments");
        return 0;
    }
    int count = 0;
    if (add_bos && count < max_tokens) {
        out_tokens[count++] = 3; // use 3 as BOS
    }
    const char *delim = " ";
    char *copy = strdup(text);
    if (!copy) {
        if (err) *err = 1;
        set_error("Out of memory");
        return 0;
    }
    char *token = strtok(copy, delim);
    while (token && count < max_tokens) {
        out_tokens[count++] = fake_vocab_token(token);
        token = strtok(NULL, delim);
    }
    free(copy);
    return count;
}

int lp_eval(lp_context* context, const int* tokens, int n_tokens, int* err) {
    (void)tokens;
    (void)n_tokens;
    set_error(NULL);
    if (err) *err = 0;
    if (!context) {
        if (err) *err = 1;
        set_error("Context is null");
        return 1;
    }
    context->step = 0;
    context->sampler_state = 0;
    context->first_token_ms = 0.0;
    context->tokens_emitted = 0;
    context->eval_start_ms = now_ms();
    return 0;
}

static int sample_internal(lp_context* context, float temp, float top_p, int top_k, float repeat_penalty, int seed, const char* grammar, int* state_pos, int* err) {
    (void)temp; (void)top_p; (void)top_k; (void)repeat_penalty; (void)grammar;
    set_error(NULL);
    if (err) *err = 0;
    if (!context) {
        if (err) *err = 1;
        set_error("Context is null");
        return 0;
    }
    if (state_pos) {
        context->sampler_state = *state_pos;
    }
    context->seed = seed;
    int sequence[] = {2, 5, 0};
    int seq_len = (int)(sizeof(sequence) / sizeof(sequence[0]));
    int index = (context->seed + context->sampler_state) % seq_len;
    context->step++;
    context->sampler_state++;
    if (state_pos) {
        *state_pos = context->sampler_state;
    }
    if (context->tokens_emitted == 0) {
        double now = now_ms();
        context->first_token_ms = now - context->eval_start_ms;
    }
    if (index < 0) index = 0;
    if (index >= seq_len) index = index % seq_len;
    int token = sequence[index];
    if (token != 0) {
        context->tokens_emitted++;
    }
    return token;
}

int lp_sample(lp_context* context, float temp, float top_p, int top_k, float repeat_penalty, int seed, int* err) {
    return sample_internal(context, temp, top_p, top_k, repeat_penalty, seed, NULL, NULL, err);
}

int lp_sample_ex(lp_context* context, float temp, float top_p, int top_k, float repeat_penalty, int seed, const char* grammar, int* state_pos, int* err) {
    return sample_internal(context, temp, top_p, top_k, repeat_penalty, seed, grammar, state_pos, err);
}

int lp_token_to_piece(lp_model* model, int token, char* out, int out_len, int* err) {
    (void)model;
    set_error(NULL);
    if (err) *err = 0;
    if (!out || out_len <= 0) {
        if (err) *err = 1;
        set_error("Invalid buffer");
        return 1;
    }
    const char* piece = "";
    switch (token) {
        case 0: piece = ""; break;
        case 1: piece = "Hello"; break;
        case 2: piece = " world"; break;
        case 3: piece = "<BOS>"; break;
        case 4: piece = " token"; break;
        case 5: piece = "!"; break;
        default: piece = "?"; break;
    }
    size_t len = strlen(piece);
    if ((int)len >= out_len) {
        if (err) *err = 1;
        set_error("Buffer too small");
        return 1;
    }
    memcpy(out, piece, len + 1);
    return 0;
}

void lp_free_model(lp_model* model) {
    if (!model) return;
    free(model->path);
    free(model);
}

void lp_free_context(lp_context* context) {
    if (!context) return;
    free(context);
}

int lp_embeddings_dim(lp_model* model, int* err) {
    (void)model;
    set_error(NULL);
    if (err) *err = 0;
    return 8;
}

int lp_get_embeddings(lp_context* context, const char* text, float* out, int max_len, int* err) {
    set_error(NULL);
    if (err) *err = 0;
    if (!context || !out || max_len <= 0) {
        if (err) *err = 1;
        set_error("Invalid buffer");
        return 0;
    }
    int dim = lp_embeddings_dim(context->model, err);
    if (dim > max_len) {
        if (err) *err = 1;
        set_error("Buffer too small for embeddings");
        return 0;
    }
    size_t text_len = text ? strlen(text) : 0;
    for (int i = 0; i < dim; i++) {
        out[i] = (float)((text_len + i) % 7) / 7.0f;
    }
    return dim;
}

int lp_get_last_stats(lp_context* context, lp_inference_stats* out, int* err) {
    set_error(NULL);
    if (err) *err = 0;
    if (!context || !out) {
        if (err) *err = 1;
        set_error("Invalid arguments");
        return 1;
    }
    double end_ms = now_ms();
    double total_ms = context->eval_start_ms > 0 ? (end_ms - context->eval_start_ms) : 0.0;
    out->first_token_ms = context->first_token_ms;
    out->tokens_emitted = context->tokens_emitted;
    if (total_ms > 0 && context->tokens_emitted > 0) {
        out->tokens_per_sec = (double)context->tokens_emitted / (total_ms / 1000.0);
    } else {
        out->tokens_per_sec = 0.0;
    }
    out->total_ms = total_ms;
    return 0;
}
