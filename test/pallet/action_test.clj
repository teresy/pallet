(ns pallet.action-test
  (:require
   [pallet.action :as action]
   [pallet.action-plan :as action-plan]
   [pallet.action-plan-test :as action-plan-test])
  (:use
   clojure.test))

(deftest action-test
  (is (fn? (action/action :in-sequence :script/bash :target [request] "hello")))
  (let [f (action/action :in-sequence :script/bash :target [request] "hello")]
    (is (fn? (action/action-fn f)))
    (is (= "hello" ((action/action-fn f) {})))
    (is (= {:action-plan
            {:fred
             {:id [[{:f (action/action-fn f)
                     :args []
                     :location :target
                     :action-type :script/bash
                     :execution :in-sequence}] nil]}}
            :phase :fred
            :target-id :id
            :server {:node-id :id}}
           (f {:phase :fred :target-id :id :server {:node-id :id}})))))

(deftest bash-action-test
  (is (fn? (action/bash-action [request arg] arg)))
  (let [f (action/bash-action [request arg] {:some-meta :a} arg)]
    (is (fn? (action/action-fn f)))
    (is (= "hello" ((action/action-fn f) {:a 1} "hello")))
    (is (= :a (:some-meta (meta f))))
    (is (= :a (:some-meta (meta (action/action-fn f)))))
    (is (= {:action-plan
            {:fred
             {:id [[{:f (action/action-fn f)
                     :args ["hello"]
                     :location :target
                     :action-type :script/bash
                     :execution :in-sequence}] nil]}}
            :phase :fred
            :target-id :id
            :server {:node-id :id}}
           (f {:phase :fred :target-id :id :server {:node-id :id}} "hello")))))

(action/def-bash-action test-bash-action
  "Some doc"
  [request arg]
  {:some-meta :a}
  arg)

(deftest def-bash-action-test
  (is (= '([request arg]) (:arglists (meta #'test-bash-action))))
  (is (= "Some doc" (:doc (meta #'test-bash-action))))
  (is (= :a (:some-meta (meta test-bash-action))))
  (is (fn? (action/action-fn test-bash-action)))
  (is (= "hello" ((action/action-fn test-bash-action) {:a 1} "hello")))
  (is (= :a (:some-meta (meta (action/action-fn test-bash-action)))))
  (is (= {:action-plan
          {:fred
           {:id [[{:f (action/action-fn test-bash-action)
                   :args ["hello"]
                   :location :target
                   :action-type :script/bash
                   :execution :in-sequence}] nil]}}
          :phase :fred
          :target-id :id
          :server {:node-id :id}}
         (test-bash-action
          {:phase :fred :target-id :id :server {:node-id :id}}
          "hello"))))

(deftest clj-action-test
  (is (fn? (action/clj-action [request arg] request)))
  (let [f (action/clj-action [request arg] request)]
    (is (fn? (action/action-fn f)))
    (is (= {:a 1} ((action/action-fn f) {:a 1} 1)))
    (is (= {:action-plan
            {:fred
             {:id [[{:f (action/action-fn f)
                     :args [1]
                     :location :origin
                     :action-type :fn/clojure
                     :execution :in-sequence}] nil]}}
            :phase :fred
            :target-id :id
            :server {:node-id :id}}
           (f {:phase :fred :target-id :id :server {:node-id :id}} 1))))
  (testing "execute"
    (let [x (atom nil)
          f (action/clj-action [request arg] (reset! x true) request)
          req {:phase :fred :target-id :id :server {:node-id :id}}
          req (f req 1)
          req (f req 2)]
      (is (= [[nil]
              {:action-plan
               {:fred
                {:id [[{:f (action/action-fn f)
                        :args [2]
                        :location :origin
                        :action-type :fn/clojure
                        :execution :in-sequence}
                       {:f (action/action-fn f)
                        :args [1]
                        :location :origin
                        :action-type :fn/clojure
                        :execution :in-sequence}]
                      nil]}}
               :phase :fred
               :target-id :id
               :server {:node-id :id}}]
               (action-plan/execute
                (action-plan/translate (-> req :action-plan :fred :id))
                req
                (action-plan-test/executor
                 {:script/bash {:target action-plan-test/echo}
                  :fn/clojure {:origin action-plan-test/null-result}}))))
      (is @x))))

(deftest as-clj-action-test
  (testing "with named args"
    (is (fn? (action/as-clj-action identity [request])))
    (let [f (action/as-clj-action identity [request])]
      (is (fn? (action/action-fn f)))
      (is (= {:a 1} ((action/action-fn f) {:a 1})))
      (is (= {:action-plan
              {:fred
               {:id [[{:f (action/action-fn f)
                       :args []
                       :location :origin
                       :action-type :fn/clojure
                       :execution :in-sequence}] nil]}}
              :phase :fred
              :target-id :id
              :server {:node-id :id}}
             (f {:phase :fred :target-id :id :server {:node-id :id}})))))
  (testing "with implied args"
    (is (fn? (action/as-clj-action identity)))
    (let [f (action/as-clj-action identity)]
      (is (fn? (action/action-fn f)))
      (is (= {:a 1} ((action/action-fn f) {:a 1})))
      (is (= {:action-plan
              {:fred
               {:id [[{:f (action/action-fn f)
                       :args []
                       :location :origin
                       :action-type :fn/clojure
                       :execution :in-sequence}] nil]}}
              :phase :fred
              :target-id :id
              :server {:node-id :id}}
             (f {:phase :fred :target-id :id :server {:node-id :id}}))))))

(deftest aggregated-action-test
  (is (fn? (action/aggregated-action [request args] args)))
  (let [f (action/aggregated-action [request args] (vec args))]
    (is (fn? (action/action-fn f)))
    (is (= [1] ((action/action-fn f) {:a 1} [1])))
    (let [req {:phase :fred :target-id :id :server {:node-id :id}}
          req (f req 1)
          req (f req 2)]
      (is (= [["[(1) (2)]\n"]
              {:action-plan
               {:fred
                {:id [[{:f (action/action-fn f)
                        :args [2]
                        :location :target
                        :action-type :script/bash
                        :execution :aggregated}
                       {:f (action/action-fn f)
                        :args [1]
                        :location :target
                        :action-type :script/bash
                        :execution :aggregated}]
                      nil]}}
               :phase :fred
               :target-id :id
               :server {:node-id :id}}]
               (action-plan/execute
                (action-plan/translate (-> req :action-plan :fred :id))
                req
                (action-plan-test/executor
                 {:script/bash {:target action-plan-test/echo}
                  :fn/clojure {:origin action-plan-test/null-result}})))))))


(action/def-aggregated-action
  test-aggregated-action
  "Some doc"
  [request arg]
  {:arglists '([request arg1])
   :some-meta :a}
  arg)

(deftest def-aggregated-action-test
  (is (= '([request arg1]) (:arglists (meta #'test-aggregated-action))))
  (is (= :a (:some-meta (meta #'test-aggregated-action))))
  (is (= "Some doc" (:doc (meta #'test-aggregated-action))))
  (is (= :a (:some-meta (meta test-aggregated-action))))
  (is (fn? (action/action-fn test-aggregated-action)))
  (is (= "hello" ((action/action-fn test-aggregated-action) {:a 1} "hello")))
  (is (= :a (:some-meta (meta (action/action-fn test-aggregated-action)))))
  (is (= {:action-plan
          {:fred
           {:id [[{:f (action/action-fn test-aggregated-action)
                   :args ["hello"]
                   :location :target
                   :action-type :script/bash
                   :execution :aggregated}] nil]}}
          :phase :fred
          :target-id :id
          :server {:node-id :id}}
         (test-aggregated-action
          {:phase :fred :target-id :id :server {:node-id :id}}
          "hello"))))
