#include <jni.h>

#include "forwarder_runtime.h"

#include <arpa/inet.h>
#include <memory>
#include <string>

namespace {
JavaVM* java_vm = nullptr;

std::string address_text(const redmiklab::FlowKey& key, bool source) {
    char buffer[INET6_ADDRSTRLEN]{};
    if (key.address_length == 16) {
        const auto& bytes = source ? key.source_ip : key.destination_ip;
        return inet_ntop(AF_INET6, bytes.data(), buffer, sizeof(buffer)) == nullptr ? "::" : buffer;
    }
    in_addr address{};
    address.s_addr = htonl(source ? key.source_address : key.destination_address);
    return inet_ntop(AF_INET, &address, buffer, sizeof(buffer)) == nullptr ? "0.0.0.0" : buffer;
}

const char* direction_text(redmiklab::ForwardingDirection value) {
    return value == redmiklab::ForwardingDirection::Upstream ? "UP" : "DOWN";
}

const char* stage_text(redmiklab::ForwardingStage value) {
    switch (value) {
        case redmiklab::ForwardingStage::TunObserved: return "TUNNEL_OBSERVED";
        case redmiklab::ForwardingStage::UpstreamSocketAccepted: return "UPSTREAM_SOCKET_ACCEPTED";
        case redmiklab::ForwardingStage::DownstreamSocketReceived: return "DOWNSTREAM_SOCKET_RECEIVED";
        case redmiklab::ForwardingStage::Dropped: return "FORWARDING_FAILED";
    }
    return "UNKNOWN";
}

const char* outcome_text(redmiklab::ForwardingOutcome value) {
    switch (value) {
        case redmiklab::ForwardingOutcome::Observed: return "OBSERVED";
        case redmiklab::ForwardingOutcome::Accepted: return "ACCEPTED";
        case redmiklab::ForwardingOutcome::Received: return "RECEIVED";
        case redmiklab::ForwardingOutcome::Failed: return "FAILED";
    }
    return "UNKNOWN";
}

class JavaSocketProtector {
public:
    JavaSocketProtector(JNIEnv* env, jobject protector)
        : protector_(env->NewGlobalRef(protector)) {
        env->GetJavaVM(&java_vm_);
        jclass protector_class = env->GetObjectClass(protector);
        protect_method_ = env->GetMethodID(protector_class, "protectSocket", "(I)Z");
        env->DeleteLocalRef(protector_class);
    }

    ~JavaSocketProtector() {
        JNIEnv* env = environment();
        if (env != nullptr && protector_ != nullptr) env->DeleteGlobalRef(protector_);
        detach_if_needed();
    }

    bool valid() const {
        return java_vm_ != nullptr && protector_ != nullptr && protect_method_ != nullptr;
    }

    bool protect(int file_descriptor) {
        JNIEnv* env = environment();
        if (env == nullptr || !valid()) return false;
        const jboolean result = env->CallBooleanMethod(protector_, protect_method_, file_descriptor);
        const bool failed = env->ExceptionCheck();
        if (failed) env->ExceptionClear();
        detach_if_needed();
        return !failed && result == JNI_TRUE;
    }

private:
    JNIEnv* environment() {
        attached_for_call_ = false;
        JNIEnv* env = nullptr;
        if (java_vm_ == nullptr) return nullptr;
        if (java_vm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) return env;
        if (java_vm_->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
        attached_for_call_ = true;
        return env;
    }

    void detach_if_needed() {
        if (attached_for_call_ && java_vm_ != nullptr) java_vm_->DetachCurrentThread();
        attached_for_call_ = false;
    }

    JavaVM* java_vm_ = nullptr;
    jobject protector_ = nullptr;
    jmethodID protect_method_ = nullptr;
    bool attached_for_call_ = false;
};

class JavaFlowObserver {
public:
    JavaFlowObserver(JNIEnv* env, jobject observer)
        : observer_(env->NewGlobalRef(observer)) {
        env->GetJavaVM(&java_vm_);
        jclass observer_class = env->GetObjectClass(observer);
        observe_method_ = env->GetMethodID(
            observer_class,
            "onForwardingObservation",
            "(ILjava/lang/String;ILjava/lang/String;ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;)V");
        env->DeleteLocalRef(observer_class);
    }

    ~JavaFlowObserver() {
        JNIEnv* env = environment();
        if (env != nullptr && observer_ != nullptr) env->DeleteGlobalRef(observer_);
        detach_if_needed();
    }

    bool valid() const {
        return java_vm_ != nullptr && observer_ != nullptr && observe_method_ != nullptr;
    }

    void observe(const redmiklab::ForwardingObservation& observation) {
        JNIEnv* env = environment();
        if (env == nullptr || !valid()) return;
        const auto& key = observation.key;
        const std::string source_address = address_text(key, true);
        const std::string destination_address = address_text(key, false);
        jstring source_string = env->NewStringUTF(source_address.c_str());
        jstring destination_string = env->NewStringUTF(destination_address.c_str());
        jstring direction_string = env->NewStringUTF(direction_text(observation.direction));
        jstring stage_string = env->NewStringUTF(stage_text(observation.stage));
        jstring outcome_string = env->NewStringUTF(outcome_text(observation.outcome));
        jstring reason_string = observation.reason.empty() ? nullptr : env->NewStringUTF(observation.reason.c_str());
        jstring hostname_string = observation.hostname.empty() ? nullptr : env->NewStringUTF(observation.hostname.c_str());
        env->CallVoidMethod(
            observer_,
            observe_method_,
            static_cast<jint>(key.protocol),
            source_string,
            static_cast<jint>(key.source_port),
            destination_string,
            static_cast<jint>(key.destination_port),
            direction_string,
            stage_string,
            outcome_string,
            static_cast<jlong>(observation.bytes),
            reason_string,
            hostname_string);
        env->DeleteLocalRef(source_string);
        env->DeleteLocalRef(destination_string);
        env->DeleteLocalRef(direction_string);
        env->DeleteLocalRef(stage_string);
        env->DeleteLocalRef(outcome_string);
        if (reason_string != nullptr) env->DeleteLocalRef(reason_string);
        if (hostname_string != nullptr) env->DeleteLocalRef(hostname_string);
        if (env->ExceptionCheck()) env->ExceptionClear();
        detach_if_needed();
    }

private:
    JNIEnv* environment() {
        attached_for_call_ = false;
        JNIEnv* env = nullptr;
        if (java_vm_ == nullptr) return nullptr;
        if (java_vm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) return env;
        if (java_vm_->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
        attached_for_call_ = true;
        return env;
    }

    void detach_if_needed() {
        if (attached_for_call_ && java_vm_ != nullptr) java_vm_->DetachCurrentThread();
        attached_for_call_ = false;
    }

    JavaVM* java_vm_ = nullptr;
    jobject observer_ = nullptr;
    jmethodID observe_method_ = nullptr;
    bool attached_for_call_ = false;
};
}  // namespace

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    java_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_redmiklab_app_NativeForwarderContract_nativeEngineVersion(
        JNIEnv* env,
        jobject /* this */) {
    return env->NewStringUTF("direct-forwarder-layered-2");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_redmiklab_app_NativeForwarderContract_nativeProtectSocket(
        JNIEnv* env,
        jobject /* this */,
        jobject protector,
        jint file_descriptor) {
    if (protector == nullptr || file_descriptor < 0) return JNI_FALSE;
    jclass protector_class = env->GetObjectClass(protector);
    if (protector_class == nullptr) return JNI_FALSE;
    jmethodID protect_method = env->GetMethodID(protector_class, "protectSocket", "(I)Z");
    if (protect_method == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(protector_class);
        return JNI_FALSE;
    }
    const jboolean protected_socket = env->CallBooleanMethod(protector, protect_method, file_descriptor);
    const bool failed = env->ExceptionCheck();
    if (failed) env->ExceptionClear();
    env->DeleteLocalRef(protector_class);
    return failed ? JNI_FALSE : protected_socket;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_redmiklab_app_NativeForwarderContract_nativeStart(
        JNIEnv* env,
        jobject /* this */,
        jint tunnel_file_descriptor,
        jobject protector,
        jobject observer) {
    if (tunnel_file_descriptor < 0 || protector == nullptr || observer == nullptr || java_vm == nullptr) return 0;
    auto java_protector = std::make_shared<JavaSocketProtector>(env, protector);
    auto java_observer = std::make_shared<JavaFlowObserver>(env, observer);
    if (!java_protector->valid() || !java_observer->valid()) return 0;
    auto* runtime = new redmiklab::ForwarderRuntime(
        tunnel_file_descriptor,
        [java_protector](int socket) { return java_protector->protect(socket); },
        [java_observer](const redmiklab::ForwardingObservation& observation) {
            java_observer->observe(observation);
        });
    if (!runtime->start()) {
        delete runtime;
        return 0;
    }
    return reinterpret_cast<jlong>(runtime);
}

extern "C" JNIEXPORT void JNICALL
Java_com_redmiklab_app_NativeForwarderContract_nativeStop(
        JNIEnv*,
        jobject /* this */,
        jlong handle) {
    if (handle == 0) return;
    auto* runtime = reinterpret_cast<redmiklab::ForwarderRuntime*>(handle);
    runtime->stop();
    delete runtime;
}
