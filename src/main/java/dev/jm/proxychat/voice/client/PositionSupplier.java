package dev.jm.proxychat.voice.client;

public interface PositionSupplier {

    PositionSupplier ZERO = new PositionSupplier() {

        @Override
        public float x() {
            return 0f;
        }

        @Override
        public float y() {
            return 0f;
        }

        @Override
        public float z() {
            return 0f;
        }
    };

    float x();

    float y();

    float z();
}
