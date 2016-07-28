package hex.deepwater;

import hex.Distribution;
import hex.Model;
import hex.ScoreKeeper;
import water.H2O;
import water.exceptions.H2OIllegalArgumentException;
import water.util.ArrayUtils;
import water.util.Log;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Created by arno on 7/26/16.
 */
public class DeepWaterParameters extends Model.Parameters {
    public String algoName() { return "DeepWater"; }
    public String fullName() { return "Deep Water"; }
    public String javaName() { return DeepWaterModel.class.getName(); }
    @Override protected double defaultStoppingTolerance() { return 0; }
    public DeepWaterParameters() {
      super();
      _stopping_rounds = 5;
      _ignore_const_cols = false; //allow String columns with File URIs
      _categorical_encoding = CategoricalEncodingScheme.OneHotInternal;
    }
    @Override
    public long progressUnits() {
      if (train()==null) return 1;
      return (long)Math.ceil(_epochs*train().numRows());
    }

    /**
     * If enabled, store the best model under the destination key of this model at the end of training.
     * Only applicable if training is not cancelled.
     */
    public boolean _overwrite_with_best_model = true;

    public boolean _autoencoder = false;

    /**
     * The number of passes over the training dataset to be carried out.
     * It is recommended to start with lower values for initial experiments.
     * This value can be modified during checkpoint restarts and allows continuation
     * of selected models.
     */
    public double _epochs = 10;

    /**
     * The number of training data rows to be processed per iteration. Note that
     * independent of this parameter, each row is used immediately to update the model
     * with (online) stochastic gradient descent. This parameter controls the
     * synchronization period between nodes in a distributed environment and the
     * frequency at which scoring and model cancellation can happen. For example, if
     * it is set to 10,000 on H2O running on 4 nodes, then each node will
     * process 2,500 rows per iteration, sampling randomly from their local data.
     * Then, model averaging between the nodes takes place, and scoring can happen
     * (dependent on scoring interval and duty factor). Special values are 0 for
     * one epoch per iteration, -1 for processing the maximum amount of data
     * per iteration (if **replicate training data** is enabled, N epochs
     * will be trained per iteration on N nodes, otherwise one epoch). Special value
     * of -2 turns on automatic mode (auto-tuning).
     */
    public long _train_samples_per_iteration = -2;

    public double _target_ratio_comm_to_comp = 0.05;

  /*Learning Rate*/
    /**
     * When adaptive learning rate is disabled, the magnitude of the weight
     * updates are determined by the user specified learning rate
     * (potentially annealed), and are a function  of the difference
     * between the predicted value and the target value. That difference,
     * generally called delta, is only available at the output layer. To
     * correct the output at each hidden layer, back propagation is
     * used. Momentum modifies back propagation by allowing prior
     * iterations to influence the current update. Using the momentum
     * parameter can aid in avoiding local minima and the associated
     * instability. Too much momentum can lead to instabilities, that's
     * why the momentum is best ramped up slowly.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double _rate = .005;

    /**
     * Learning rate annealing reduces the learning rate to "freeze" into
     * local minima in the optimization landscape.  The annealing rate is the
     * inverse of the number of training samples it takes to cut the learning rate in half
     * (e.g., 1e-6 means that it takes 1e6 training samples to halve the learning rate).
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double _rate_annealing = 1e-6;

    /**
     * The momentum_start parameter controls the amount of momentum at the beginning of training.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double _momentum_start = 0.9;

    /**
     * The momentum_ramp parameter controls the amount of learning for which momentum increases
     * (assuming momentum_stable is larger than momentum_start). The ramp is measured in the number
     * of training samples.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double _momentum_ramp = 1e4;

    /**
     * The momentum_stable parameter controls the final momentum value reached after momentum_ramp training samples.
     * The momentum used for training will remain the same for training beyond reaching that point.
     * This parameter is only active if adaptive learning rate is disabled.
     */
    public double _momentum_stable = 0.99;


    /**
     * The minimum time (in seconds) to elapse between model scoring. The actual
     * interval is determined by the number of training samples per iteration and the scoring duty cycle.
     */
    public double _score_interval = 5;

    /**
     * The number of training dataset points to be used for scoring. Will be
     * randomly sampled. Use 0 for selecting the entire training dataset.
     */
    public long _score_training_samples = 10000l;

    /**
     * The number of validation dataset points to be used for scoring. Can be
     * randomly sampled or stratified (if "balance classes" is set and "score
     * validation sampling" is set to stratify). Use 0 for selecting the entire
     * training dataset.
     */
    public long _score_validation_samples = 0l;

    /**
     * Maximum fraction of wall clock time spent on model scoring on training and validation samples,
     * and on diagnostics such as computation of feature importances (i.e., not on training).
     */
    public double _score_duty_cycle = 0.1;

    /**
     * Enable quiet mode for less output to standard output.
     */
    public boolean _quiet_mode = false;

    /**
     * Replicate the entire training dataset onto every node for faster training on small datasets.
     */
    public boolean _replicate_training_data = true;

    /**
     * Run on a single node for fine-tuning of model parameters. Can be useful for
     * checkpoint resumes after training on multiple nodes for fast initial
     * convergence.
     */
    public boolean _single_node_mode = false;

    /**
     * Enable shuffling of training data (on each node). This option is
     * recommended if training data is replicated on N nodes, and the number of training samples per iteration
     * is close to N times the dataset size, where all nodes train with (almost) all
     * the data. It is automatically enabled if the number of training samples per iteration is set to -1 (or to N
     * times the dataset size or larger).
     */
    public boolean _shuffle_training_data = true;

    public int _mini_batch_size = 32;

    /**
     * Validate model parameters
     * @param dl DL Model Builder (Driver)
     * @param expensive (whether or not this is the "final" check)
     */
    void validate(DeepWater dl, boolean expensive) {
      boolean classification = expensive || dl.nclasses() != 0 ? dl.isClassifier() : _distribution == Distribution.Family.bernoulli || _distribution == Distribution.Family.multinomial;
      if (_mini_batch_size < 1)
        dl.error("_mini_batch_size", "Mini-batch size must be >= 1");

      if (!_autoencoder) {
        if (_valid == null)
          dl.hide("_score_validation_samples", "score_validation_samples requires a validation frame.");

        if (classification) {
          dl.hide("_regression_stop", "regression_stop is used only with regression.");
        } else {
          dl.hide("_classification_stop", "classification_stop is used only with classification.");
  //          dl.hide("_max_hit_ratio_k", "max_hit_ratio_k is used only with classification.");
  //          dl.hide("_balance_classes", "balance_classes is used only with classification.");
        }
  //        if( !classification || !_balance_classes )
  //          dl.hide("_class_sampling_factors", "class_sampling_factors requires both classification and balance_classes.");
        if (!classification && _valid != null || _valid == null)
          dl.hide("_score_validation_sampling", "score_validation_sampling requires classification and a validation frame.");
      } else {
        if (_nfolds > 1) {
          dl.error("_nfolds", "N-fold cross-validation is not supported for Autoencoder.");
        }
      }
      if (_categorical_encoding==CategoricalEncodingScheme.Enum) {
        dl.error("_categorical_encoding", "Cannot use Enum encoding for categoricals - need numbers!");
      }
      if (_categorical_encoding==CategoricalEncodingScheme.OneHotExplicit) {
        dl.error("_categorical_encoding", "Won't use explicit Enum encoding for categoricals - it's much faster with OneHotInternal!");
      }
      if (H2O.CLOUD.size() == 1 && _replicate_training_data)
        dl.hide("_replicate_training_data", "replicate_training_data is only valid with cloud size greater than 1.");
      if (_single_node_mode && (H2O.CLOUD.size() == 1 || !_replicate_training_data))
        dl.hide("_single_node_mode", "single_node_mode is only used with multi-node operation with replicated training data.");
      if (H2O.ARGS.client && _single_node_mode)
        dl.error("_single_node_mode", "Cannot run on a single node in client mode");
      if (_autoencoder)
        dl.hide("_use_all_factor_levels", "use_all_factor_levels is mandatory in combination with autoencoder.");
      if (_nfolds != 0)
        dl.hide("_overwrite_with_best_model", "overwrite_with_best_model is unsupported in combination with n-fold cross-validation.");
      if (expensive) dl.checkDistributions();

      if (_score_training_samples < 0)
        dl.error("_score_training_samples", "Number of training samples for scoring must be >= 0 (0 for all).");
      if (_score_validation_samples < 0)
        dl.error("_score_validation_samples", "Number of training samples for scoring must be >= 0 (0 for all).");
      if (classification && dl.hasOffsetCol())
        dl.error("_offset_column", "Offset is only supported for regression.");

      // reason for the error message below is that validation might not have the same horizontalized features as the training data (or different order)
      if (expensive) {
        if (!classification && _balance_classes) {
          dl.error("_balance_classes", "balance_classes requires classification.");
        }
        if (_class_sampling_factors != null && !_balance_classes) {
          dl.error("_class_sampling_factors", "class_sampling_factors requires balance_classes to be enabled.");
        }
        if (_replicate_training_data && null != train() && train().byteSize() > 0.9*H2O.CLOUD.free_mem()/H2O.CLOUD.size() && H2O.CLOUD.size() > 1) {
          dl.error("_replicate_training_data", "Compressed training dataset takes more than 90% of avg. free available memory per node (" + 0.9*H2O.CLOUD.free_mem()/H2O.CLOUD.size() + "), cannot run with replicate_training_data.");
        }
      }
      if (_autoencoder && _stopping_metric != ScoreKeeper.StoppingMetric.AUTO && _stopping_metric != ScoreKeeper.StoppingMetric.MSE) {
        dl.error("_stopping_metric", "Stopping metric must either be AUTO or MSE for autoencoder.");
      }
    }

    static class Sanity {
      // the following parameters can be modified when restarting from a checkpoint
      transient static private final String[] cp_modifiable = new String[]{
              "_seed",
              "_checkpoint",
              "_epochs",
              "_score_interval",
              "_train_samples_per_iteration",
              "_target_ratio_comm_to_comp",
              "_score_duty_cycle",
              "_score_training_samples",
              "_score_validation_samples",
              "_score_validation_sampling",
              "_classification_stop",
              "_regression_stop",
              "_stopping_rounds",
              "_stopping_metric",
              "_stopping_tolerance",
              "_quiet_mode",
              "_max_confusion_matrix_size",
              "_max_hit_ratio_k",
              "_diagnostics",
              "_variable_importances",
              "_initial_weight_distribution", //will be ignored anyway
              "_initial_weight_scale", //will be ignored anyway
              "_initial_weights",
              "_initial_biases",
              "_force_load_balance",
              "_replicate_training_data",
              "_shuffle_training_data",
              "_single_node_mode",
              "_fast_mode",
              // Allow modification of the regularization parameters after a checkpoint restart
              "_l1",
              "_l2",
              "_max_w2",
              "_input_dropout_ratio",
              "_hidden_dropout_ratios",
              "_loss",
              "_overwrite_with_best_model",
              "_missing_values_handling",
              "_average_activation",
              "_reproducible",
              "_export_weights_and_biases",
              "_elastic_averaging",
              "_elastic_averaging_moving_rate",
              "_elastic_averaging_regularization",
              "_mini_batch_size",
              "_pretrained_autoencoder"
      };

      // the following parameters must not be modified when restarting from a checkpoint
      transient static private final String[] cp_not_modifiable = new String[]{
              "_drop_na20_cols",
              "_response_column",
              "_activation",
              "_use_all_factor_levels",
              "_standardize",
              "_adaptive_rate",
              "_autoencoder",
              "_rho",
              "_epsilon",
              "_sparse",
              "_sparsity_beta",
              "_col_major",
              "_rate",
              "_rate_annealing",
              "_rate_decay",
              "_momentum_start",
              "_momentum_ramp",
              "_momentum_stable",
              "_nesterov_accelerated_gradient",
              "_ignore_const_cols",
              "_max_categorical_features",
              "_nfolds",
              "_distribution",
              "_quantile_alpha",
              "_huber_alpha",
              "_tweedie_power"
      };

      static void checkCompleteness() {
        for (Field f : hex.deepwater.DeepWaterParameters.class.getDeclaredFields())
          if (!ArrayUtils.contains(cp_not_modifiable, f.getName())
                  &&
                  !ArrayUtils.contains(cp_modifiable, f.getName())
                  ) {
            if (f.getName().equals("_hidden")) continue;
            if (f.getName().equals("_ignored_columns")) continue;
	    if (f.getName().equals("$jacocoData")) continue; // If code coverage is enabled
            throw H2O.unimpl("Please add " + f.getName() + " to either cp_modifiable or cp_not_modifiable");
          }
      }

      /**
       * Check that checkpoint continuation is possible
       *
       * @param oldP old DL parameters (from checkpoint)
       * @param newP new DL parameters (user-given, to restart from checkpoint)
       */
      static void checkIfParameterChangeAllowed(final hex.deepwater.DeepWaterParameters oldP, final hex.deepwater.DeepWaterParameters newP) {
        checkCompleteness();
        if (newP._nfolds != 0)
          throw new UnsupportedOperationException("nfolds must be 0: Cross-validation is not supported during checkpoint restarts.");
        if ((newP._valid == null) != (oldP._valid == null)) {
          throw new H2OIllegalArgumentException("Presence of validation dataset must agree with the checkpointed model.");
        }
        if (!newP._autoencoder && (newP._response_column == null || !newP._response_column.equals(oldP._response_column))) {
          throw new H2OIllegalArgumentException("Response column (" + newP._response_column + ") is not the same as for the checkpointed model: " + oldP._response_column);
        }
        if (!Arrays.equals(newP._ignored_columns, oldP._ignored_columns)) {
          throw new H2OIllegalArgumentException("Ignored columns must be the same as for the checkpointed model.");
        }

        //compare the user-given parameters before and after and check that they are not changed
        for (Field fBefore : oldP.getClass().getFields()) {
          if (ArrayUtils.contains(cp_not_modifiable, fBefore.getName())) {
            for (Field fAfter : newP.getClass().getFields()) {
              if (fBefore.equals(fAfter)) {
                try {
                  if (fAfter.get(newP) == null || fBefore.get(oldP) == null || !fBefore.get(oldP).toString().equals(fAfter.get(newP).toString())) { // if either of the two parameters is null, skip the toString()
                    if (fBefore.get(oldP) == null && fAfter.get(newP) == null)
                      continue; //if both parameters are null, we don't need to do anything
                    throw new H2OIllegalArgumentException("Cannot change parameter: '" + fBefore.getName() + "': " + fBefore.get(oldP) + " -> " + fAfter.get(newP));
                  }
                } catch (IllegalAccessException e) {
                  e.printStackTrace();
                }
              }
            }
          }
        }
      }

      /**
       * Update the parameters from checkpoint to user-specified
       *
       * @param srcParms     source: user-specified parameters
       * @param tgtParms     target: parameters to be modified
       * @param doIt         whether to overwrite target parameters (or just print the message)
       * @param quiet        whether to suppress the notifications about parameter changes
       */
      static void updateParametersDuringCheckpointRestart(hex.deepwater.DeepWaterParameters srcParms, hex.deepwater.DeepWaterParameters tgtParms/*actually used during training*/, boolean doIt, boolean quiet) {
        for (Field fTarget : tgtParms.getClass().getFields()) {
          if (ArrayUtils.contains(cp_modifiable, fTarget.getName())) {
            for (Field fSource : srcParms.getClass().getFields()) {
              if (fTarget.equals(fSource)) {
                try {
                  if (fSource.get(srcParms) == null || fTarget.get(tgtParms) == null || !fTarget.get(tgtParms).toString().equals(fSource.get(srcParms).toString())) { // if either of the two parameters is null, skip the toString()
                    if (fTarget.get(tgtParms) == null && fSource.get(srcParms) == null)
                      continue; //if both parameters are null, we don't need to do anything
                    if (!tgtParms._quiet_mode && !quiet)
                      Log.info("Applying user-requested modification of '" + fTarget.getName() + "': " + fTarget.get(tgtParms) + " -> " + fSource.get(srcParms));
                    if (doIt)
                      fTarget.set(tgtParms, fSource.get(srcParms));
                  }
                } catch (IllegalAccessException e) {
                  e.printStackTrace();
                }
              }
            }
          }
        }
      }

      /**
       * Take user-given parameters and turn them into usable, fully populated parameters (e.g., to be used by Neurons during training)
       *
       * @param fromParms      raw user-given parameters from the REST API (READ ONLY)
       * @param toParms        modified set of parameters, with defaults filled in (WILL BE MODIFIED)
       * @param nClasses       number of classes (1 for regression or autoencoder)
       */
      static void modifyParms(hex.deepwater.DeepWaterParameters fromParms, hex.deepwater.DeepWaterParameters toParms, int nClasses) {
        if (H2O.CLOUD.size() == 1 && fromParms._replicate_training_data) {
          if (!fromParms._quiet_mode)
            Log.info("_replicate_training_data: Disabling replicate_training_data on 1 node.");
          toParms._replicate_training_data = false;
        }
        if (fromParms._single_node_mode && (H2O.CLOUD.size() == 1 || !fromParms._replicate_training_data)) {
          if (!fromParms._quiet_mode)
            Log.info("_single_node_mode: Disabling single_node_mode (only for multi-node operation with replicated training data).");
          toParms._single_node_mode = false;
        }
        if (fromParms._overwrite_with_best_model && fromParms._nfolds != 0) {
          if (!fromParms._quiet_mode)
            Log.info("_overwrite_with_best_model: Disabling overwrite_with_best_model in combination with n-fold cross-validation.");
          toParms._overwrite_with_best_model = false;
        }
        if (fromParms._categorical_encoding==CategoricalEncodingScheme.AUTO) {
          if (!fromParms._quiet_mode)
            Log.info("_categorical_encoding: Automatically enabling OneHotInternal categorical encoding.");
          toParms._categorical_encoding = CategoricalEncodingScheme.OneHotInternal;
         }
        if (fromParms._nfolds != 0) {
          if (fromParms._overwrite_with_best_model) {
            if (!fromParms._quiet_mode)
              Log.info("_overwrite_with_best_model: Automatically disabling overwrite_with_best_model, since the final model is the only scored model with n-fold cross-validation.");
            toParms._overwrite_with_best_model = false;
          }
        }
        if (fromParms._autoencoder && fromParms._stopping_metric == ScoreKeeper.StoppingMetric.AUTO) {
          if (!fromParms._quiet_mode)
            Log.info("_stopping_metric: Automatically setting stopping_metric to MSE for autoencoder.");
          toParms._stopping_metric = ScoreKeeper.StoppingMetric.MSE;
        }

        // Automatically set the distribution
        if (fromParms._distribution == Distribution.Family.AUTO) {
          // For classification, allow AUTO/bernoulli/multinomial with losses CrossEntropy/Quadratic/Huber/Absolute
          if (nClasses > 1) {
            toParms._distribution = nClasses == 2 ? Distribution.Family.bernoulli : Distribution.Family.multinomial;
          }
        }
      }
    }
  }