npx shadow-cljs release prod
clj -M -e "(compile 'clojurepod.core)"
clj -M:uberdeps --main-class clojurepod.core
