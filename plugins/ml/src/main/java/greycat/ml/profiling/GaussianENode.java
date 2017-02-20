/**
 * Copyright 2017 The GreyCat Authors.  All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package greycat.ml.profiling;


import greycat.Type;
import greycat.ml.common.matrix.MatrixOps;
import greycat.ml.common.matrix.VolatileDMatrix;
import greycat.struct.DMatrix;
import greycat.struct.ENode;

public class GaussianENode {
    //Getters and setters
    public final static String NAME = "GaussianENode";


    private ENode backend;
    //can be used for normalization
    private double[] avg = null;
    private double[] std = null;
    private DMatrix cov = null;


    public GaussianENode(ENode backend) {
        if (backend == null) {
            throw new RuntimeException("backend can't be null for Gaussian node!");
        }
        this.backend = backend;
    }

    public void setPrecisions(double[] precisions) {
        backend.set(Gaussian.PRECISIONS, Type.DOUBLE_ARRAY, precisions);
    }

    public void learn(double[] values) {
        int features = values.length;
        int total = backend.getWithDefault(Gaussian.TOTAL, 0);
        //Create dirac only save total and sum
        if (total == 0) {
            double[] sum = new double[features];
            System.arraycopy(values, 0, sum, 0, features);
            total = 1;
            backend.set(Gaussian.TOTAL, Type.INT, total);
            backend.set(Gaussian.SUM, Type.DOUBLE_ARRAY, sum);

            //set total, weight, sum, return
        } else {
            double[] sum;
            double[] min;
            double[] max;
            double[] sumsquares;

            sum = (double[]) backend.get(Gaussian.SUM);
            if (features != sum.length) {
                throw new RuntimeException("Input dimensions have changed!");
            }
            //Upgrade dirac to gaussian
            if (total == 1) {
                //Create getMin, getMax, sumsquares
                min = new double[features];
                max = new double[features];
                System.arraycopy(sum, 0, min, 0, features);
                System.arraycopy(sum, 0, max, 0, features);
                sumsquares = new double[features * (features + 1) / 2];
                int count = 0;
                for (int i = 0; i < features; i++) {
                    for (int j = i; j < features; j++) {
                        sumsquares[count] = sum[i] * sum[j];
                        count++;
                    }
                }
            }
            //Otherwise, get previously stored values
            else {
                min = (double[]) backend.get(Gaussian.MIN);
                max = (double[]) backend.get(Gaussian.MAX);
                sumsquares = (double[]) backend.get(Gaussian.SUMSQ);
            }

            //Update the values
            for (int i = 0; i < features; i++) {
                if (values[i] < min[i]) {
                    min[i] = values[i];
                }

                if (values[i] > max[i]) {
                    max[i] = values[i];
                }
                sum[i] += values[i];
            }

            int count = 0;
            for (int i = 0; i < features; i++) {
                for (int j = i; j < features; j++) {
                    sumsquares[count] += values[i] * values[j];
                    count++;
                }
            }
            total++;
            //Store everything
            backend.set(Gaussian.TOTAL, Type.INT, total);
            backend.set(Gaussian.SUM, Type.DOUBLE_ARRAY, sum);
            backend.set(Gaussian.MIN, Type.DOUBLE_ARRAY, min);
            backend.set(Gaussian.MAX, Type.DOUBLE_ARRAY, max);
            backend.set(Gaussian.SUMSQ, Type.DOUBLE_ARRAY, sumsquares);
        }
        // set all cached avg, std, and cov arrays to null
        invalidate();
    }

    private void invalidate() {
        avg = null;
        std = null;
        cov = null;
    }


    private boolean initAvg() {
        if (avg != null) {
            return true;
        }

        int total = backend.getWithDefault(Gaussian.TOTAL, 0);
        if (total != 0) {
            double[] sum = (double[]) backend.get(Gaussian.SUM);
            avg = new double[sum.length];
            for (int i = 0; i < sum.length; i++) {
                avg[i] = sum[i] / total;
            }
            return true;
        } else {
            return false;
        }
    }


    private boolean initStd() {
        if (std != null) {
            return true;
        }
        int total = backend.getWithDefault(Gaussian.TOTAL, 0);
        if (total >= 2) {
            initAvg();
            int dim = avg.length;
            double[] err = backend.getWithDefault(Gaussian.PRECISIONS, new double[avg.length]);
            double[] sumsq = getSumSq();
            std = new double[dim];

            double correction = total;
            correction = correction / (total - 1);

            int count = 0;
            for (int i = 0; i < dim; i++) {
                std[i] = Math.sqrt((sumsq[count] / total - avg[i] * avg[i]) * correction);
                count += (dim - i);
                if (std[i] < err[i]) {
                    std[i] = err[i];
                }
            }
            return true;
        } else {
            return false;
        }
    }

    private boolean initCov() {
        if (cov != null) {
            return true;
        }
        int total = backend.getWithDefault(Gaussian.TOTAL, 0);
        if (total >= 2) {
            initAvg();
            int dim = avg.length;

            double[] err = backend.getWithDefault(Gaussian.PRECISIONS, new double[avg.length]);
            for (int i = 0; i < err.length; i++) {
                err[i] = err[i] * err[i];
            }

            double[] sumsq = getSumSq();
            double[] covariances = new double[dim * dim];

            double correction = total;
            correction = correction / (total - 1);

            int count = 0;
            for (int i = 0; i < dim; i++) {
                for (int j = i; j < dim; j++) {
                    covariances[i * dim + j] = (sumsq[count] / total - avg[i] * avg[j]) * correction;
                    covariances[j * dim + i] = covariances[i * dim + j];
                    count++;
                }
                if (covariances[i * dim + i] < err[i]) {
                    covariances[i * dim + i] = err[i];
                }
            }
            cov = VolatileDMatrix.wrap(covariances, dim, dim);
            return true;
        } else {
            return false;
        }

    }


    public double[] getAvg() {
        if (!initAvg()) {
            return null;
        }
        double[] tempAvg = new double[avg.length];
        System.arraycopy(avg, 0, tempAvg, 0, avg.length);
        return tempAvg;
    }

    public double[] getSTD() {
        if (!initStd()) {
            return null;
        }
        double[] tempStd = new double[std.length];
        System.arraycopy(std, 0, tempStd, 0, std.length);
        return tempStd;
    }

    public DMatrix getCovariance() {
        if (!initCov()) {
            return null;
        }
        VolatileDMatrix covtemp = VolatileDMatrix.empty(cov.rows(), cov.columns());
        MatrixOps.copy(cov, covtemp);
        return covtemp;
    }

    public double[] getSum() {
        int total = backend.getWithDefault(Gaussian.TOTAL, 0);
        if (total != 0) {
            return (double[]) backend.get(Gaussian.SUM);
        } else {
            return null;
        }
    }

    public double[] getSumSq() {
        int total = backend.getWithDefault(Gaussian.TOTAL, 0);
        if (total == 0) {
            return null;
        }
        if (total == 1) {
            double[] sum = (double[]) backend.get(Gaussian.SUM);

            int features = sum.length;
            double[] sumsquares = new double[features * (features + 1) / 2];
            int count = 0;
            for (int i = 0; i < features; i++) {
                for (int j = i; j < features; j++) {
                    sumsquares[count] = sum[i] * sum[j];
                    count++;
                }
            }
            return sumsquares;
        } else {
            return (double[]) backend.get(Gaussian.SUMSQ);
        }
    }


    public double[] getMin() {
        int total = backend.getWithDefault(Gaussian.TOTAL, 0);
        if (total == 0) {
            return null;
        }
        if (total == 1) {
            return (double[]) backend.get(Gaussian.SUM);
        } else {
            return (double[]) backend.get(Gaussian.MIN);
        }
    }

    public double[] getMax() {
        int total = backend.getWithDefault(Gaussian.TOTAL, 0);
        if (total == 0) {
            return null;
        }
        if (total == 1) {
            return (double[]) backend.get(Gaussian.SUM);
        } else {
            return (double[]) backend.get(Gaussian.MAX);
        }
    }


    public int getTotal() {
        return backend.getWithDefault(Gaussian.TOTAL, 0);
    }

    public int getDimensions() {
        int total = backend.getWithDefault(Gaussian.TOTAL, 0);
        if (total != 0) {
            return ((double[]) backend.get(Gaussian.SUM)).length;
        } else {
            return 0;
        }
    }


    public double[] normalize(double[] input) {
        if (!initStd()) {
            throw new RuntimeException("can't normalize yet, not enough data!");
        }

        double[] res = new double[input.length];

        for (int i = 0; i < input.length; i++) {
            if (std[i] != 0) {
                res[i] = (input[i] - avg[i]) / std[i];
            } else {
                res[i] = 0;
            }
        }

        return res;
    }

    public double[] inverseNormalise(double[] input) {
        if (!initStd()) {
            throw new RuntimeException("can't normalize yet, not enough data!");
        }

        double[] res = new double[input.length];

        for (int i = 0; i < input.length; i++) {
            res[i] = input[i] * std[i] + avg[i];
        }
        return res;
    }

    public double[] normalizeMinMax(double[] input) {
        if (!initAvg()) {
            throw new RuntimeException("can't normalize yet, not enough data!");
        }

        double[] res = new double[input.length];
        double[] max = getMax();
        double[] min = getMin();

        for (int i = 0; i < input.length; i++) {
            if ((max[i] - min[i]) != 0) {
                res[i] = (input[i] - min[i]) / (max[i] - min[i]);
            } else {
                res[i] = 0;
            }
        }

        return res;
    }

    public double[] inverseNormaliseMinMax(double[] input) {
        if (!initAvg()) {
            throw new RuntimeException("can't normalize yet, not enough data!");
        }

        double[] res = new double[input.length];
        double[] max = getMax();
        double[] min = getMin();

        for (int i = 0; i < input.length; i++) {
            res[i] = input[i] * (max[i] - min[i]) + min[i];
        }
        return res;
    }
}
