/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */
package io.reactivex.internal.operators.observable;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;

public final class ObservableRange extends Observable<Integer> {
    private final int start;
    private final int count;

    public ObservableRange(int start, int count) {
        this.start = start;
        this.count = count;
    }

    @Override
    protected void subscribeActual(Observer<? super Integer> o) {
        Disposable d = Disposables.empty();
        o.onSubscribe(d);

        long end = start - 1L + count;
        for (long i = start; i <= end && !d.isDisposed(); i++) {
            o.onNext((int)i);
        }
        if (!d.isDisposed()) {
            o.onComplete();
        }
    }
}