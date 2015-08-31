(ns uber.test-runner
  (:require
   [cljs.test :refer-macros [run-tests]]
   [uber.core-test]))

(enable-console-print!)

(defn runner []
  (if (cljs.test/successful?
       (run-tests
        'uber.core-test))
    0
    1))
